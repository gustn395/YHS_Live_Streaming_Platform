#!/usr/bin/env bash
# POC-01 자동 확인 (MediaMTX·Caddy 컨테이너 실행, 설정, 포트, HTTPS, WHIP/WHEP 엔드포인트 응답)
#
# 주의: 이 스크립트는 "서버가 준비되었는지"만 확인한다.
#       WHIP/WHEP 엔드포인트가 응답해도 실제 송출·재생 성공을 뜻하지 않는다. (POC-02~10은 실제 기기로 확인)
#
# 사용법: poc/mediamtx-webrtc 폴더에서  bash scripts/smoke.sh
# 필요 도구: docker(compose), curl, openssl
set -u

cd "$(dirname "$0")/.."

EXPECTED_MEDIAMTX_IMAGE="bluenviron/mediamtx:1.21.4"
EXPECTED_CADDY_IMAGE="caddy:2.11.9-alpine"
HTTPS_PORT=8443
API="http://127.0.0.1:9997"

pass=0
fail=0
ok()   { echo "  [PASS] $*"; pass=$((pass + 1)); }
ng()   { echo "  [FAIL] $*"; fail=$((fail + 1)); }
info() { echo "         $*"; }

if [ ! -f .env ]; then
  echo "[FAIL] .env가 없습니다. cp .env.example .env 후 POC_LAN_IP를 설정하세요."
  exit 1
fi
LAN_IP=$(grep -E '^POC_LAN_IP=' .env | tail -1 | cut -d= -f2 | tr -d '[:space:]\r')
BASE="https://${LAN_IP}:${HTTPS_PORT}"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "== POC-01 smoke test ($(date '+%Y-%m-%d %H:%M:%S'))"
echo "   POC_LAN_IP=${LAN_IP}"

echo "-- 1. 컨테이너"
for svc in mediamtx caddy; do
  st=$(docker compose ps --format '{{.State}}' "$svc" 2>/dev/null)
  [ "$st" = "running" ] && ok "$svc 실행 중" || ng "$svc 상태: ${st:-없음}"
done
img=$(docker compose ps --format '{{.Image}}' mediamtx 2>/dev/null)
[ "$img" = "$EXPECTED_MEDIAMTX_IMAGE" ] && ok "MediaMTX 이미지 $img" || ng "MediaMTX 이미지 '$img' (기대값 $EXPECTED_MEDIAMTX_IMAGE)"
img=$(docker compose ps --format '{{.Image}}' caddy 2>/dev/null)
[ "$img" = "$EXPECTED_CADDY_IMAGE" ] && ok "Caddy 이미지 $img" || ng "Caddy 이미지 '$img' (기대값 $EXPECTED_CADDY_IMAGE)"
ver=$(docker compose logs mediamtx 2>/dev/null | grep -oE 'MediaMTX v[0-9.]+' | tail -1)
[ -n "$ver" ] && ok "로그의 버전: $ver" || ng "로그에서 MediaMTX 버전을 찾지 못함"
errs=$(docker compose logs mediamtx 2>/dev/null | grep -cE '\b(ERR|ERROR)\b')
[ "$errs" = "0" ] && ok "MediaMTX 로그에 ERR 없음" || ng "MediaMTX 로그에 ERR ${errs}건 (docker compose logs mediamtx 확인)"

echo "-- 2. Control API (localhost 전용)"
cfg=$(curl -s --max-time 5 "$API/v3/config/global/get")
if [ -z "$cfg" ]; then
  ng "API 응답 없음 ($API)"
else
  ok "API 응답 ($API/v3/config/global/get)"
  for kv in '"webrtc":true' '"rtsp":false' '"rtmp":false' '"hls":false' '"srt":false' '"webrtcIPsFromInterfaces":false' '"webrtcLocalUDPAddress":":8189"'; do
    echo "$cfg" | grep -q "$kv" && ok "설정 $kv" || ng "설정 $kv 아님"
  done
  echo "$cfg" | grep -qE "\"webrtcAdditionalHosts\":\[\"${LAN_IP//./\\.}\"\]" \
    && ok "webrtcAdditionalHosts = [\"$LAN_IP\"]" \
    || ng "webrtcAdditionalHosts에 $LAN_IP 없음: $(echo "$cfg" | grep -oE '"webrtcAdditionalHosts":\[[^]]*\]')"
  echo "$cfg" | grep -qE '"webrtcICEServers2":\[\]' && ok "ICE 서버(STUN/TURN) 없음 (LAN 전용)" || ng "ICE 서버가 설정되어 있음"
fi
paths=$(curl -s --max-time 5 "$API/v3/paths/list")
echo "$paths" | grep -q '"itemCount"' && ok "경로 목록 API: $(echo "$paths" | grep -oE '"itemCount":[0-9]+')" || ng "경로 목록 API 실패"
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://${LAN_IP}:9997/v3/paths/list")
[ "$code" = "000" ] && ok "LAN IP:9997에서 API 접근 불가 (127.0.0.1 바인딩)" || ng "LAN IP:9997에서 API 응답 $code (외부 노출됨)"

echo "-- 3. 포트 바인딩"
docker compose port --protocol udp mediamtx 8189 >/dev/null 2>&1 \
  && ok "8189/udp → $(docker compose port --protocol udp mediamtx 8189)" || ng "8189/udp 포트 매핑 없음"
p=$(docker compose port mediamtx 8889 2>/dev/null)
[[ "$p" == 127.0.0.1:* ]] && ok "8889/tcp → $p (localhost 전용)" || ng "8889/tcp 매핑: ${p:-없음}"
p=$(docker compose port mediamtx 9997 2>/dev/null)
[[ "$p" == 127.0.0.1:* ]] && ok "9997/tcp → $p (localhost 전용)" || ng "9997/tcp 매핑: ${p:-없음}"

echo "-- 4. Caddy HTTPS (tls internal)"
if docker compose cp caddy:/data/caddy/pki/authorities/local/root.crt "$TMP/root.crt" >/dev/null 2>&1; then
  ok "내부 루트 CA 추출: $(openssl x509 -in "$TMP/root.crt" -noout -subject 2>/dev/null)"
  info "만료: $(openssl x509 -in "$TMP/root.crt" -noout -enddate 2>/dev/null)"
  info "SHA-256: $(openssl x509 -in "$TMP/root.crt" -noout -fingerprint -sha256 2>/dev/null | cut -d= -f2)"
else
  ng "내부 루트 CA를 찾지 못함"
fi
# Windows용 curl(Schannel)은 --cacert로 사설 CA 검증을 못 하는 경우가 있어 openssl로 인증서 체인을 검증한다.
if echo | openssl s_client -connect "${LAN_IP}:${HTTPS_PORT}" -CAfile "$TMP/root.crt" -verify_ip "$LAN_IP" -verify_return_error </dev/null >"$TMP/sclient.txt" 2>&1; then
  ok "https://${LAN_IP}:${HTTPS_PORT} 인증서가 내부 CA로 검증됨 (IP SAN 일치)"
else
  ng "HTTPS 인증서 검증 실패: $(grep -m1 -iE 'verify error|errno|connect' "$TMP/sclient.txt")"
fi
code=$(curl -sk -o "$TMP/index.html" -w '%{http_code}' --max-time 5 "$BASE/")
[ "$code" = "200" ] && grep -q 'MediaMTX WebRTC POC' "$TMP/index.html" && ok "GET / → 200 (POC 페이지)" || ng "GET / → $code"
for page in publish-pc.html publish-mobile.html play.html js/common.js; do
  code=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/$page")
  [ "$code" = "200" ] && ok "GET /$page → 200" || ng "GET /$page → $code"
done
curl -sk --max-time 5 "$BASE/ca/root.crt" -o "$TMP/dl.crt"
cmp -s "$TMP/dl.crt" "$TMP/root.crt" && ok "GET /ca/root.crt → 루트 CA와 동일" || ng "GET /ca/root.crt 내용이 루트 CA와 다름"
code=$(curl -sk -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/ca/root.key")
[ "$code" != "200" ] && ok "GET /ca/root.key → $code (개인 키 미노출)" || ng "GET /ca/root.key → 200 (개인 키 노출!)"

echo "-- 5. WHIP/WHEP 엔드포인트 응답 (Caddy 경유, 송출·재생 성공 판정 아님)"
code=$(curl -sk -o /dev/null -w '%{http_code}' -X OPTIONS --max-time 5 "$BASE/live/smoke/whip")
[ "$code" = "204" ] && ok "OPTIONS /live/smoke/whip → 204" || ng "OPTIONS /live/smoke/whip → $code"
code=$(curl -sk -o /dev/null -w '%{http_code}' -X OPTIONS --max-time 5 "$BASE/live/smoke/whep")
[ "$code" = "204" ] && ok "OPTIONS /live/smoke/whep → 204" || ng "OPTIONS /live/smoke/whep → $code"
code=$(curl -sk -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/sdp' --data 'v=0' --max-time 5 "$BASE/live/smoke/whep")
[ "$code" = "404" ] && ok "송출 없는 경로 WHEP POST → 404 (정상 거절)" || info "송출 없는 경로 WHEP POST → $code (참고)"
# live/<이름> 이외의 경로는 MediaMTX가 거부해야 한다. (Caddy는 /live/*만 넘기므로 MediaMTX에 직접 확인)
body=$(curl -s -X POST -H 'Content-Type: application/sdp' --data 'v=0' --max-time 5 "http://127.0.0.1:8889/notallowed/whip")
echo "$body" | grep -q "is not configured" && ok "허용하지 않은 경로(notallowed) 송출 거부" || ng "허용하지 않은 경로 응답: $body"
body=$(curl -s -X POST -H 'Content-Type: application/sdp' --data 'v=0' --max-time 5 "http://127.0.0.1:8889/live/a/b/whip")
echo "$body" | grep -q "is not configured" && ok "허용하지 않은 경로(live/a/b) 송출 거부" || ng "허용하지 않은 경로 응답: $body"

echo
echo "== 결과: PASS ${pass} / FAIL ${fail}"
[ "$fail" -eq 0 ]
