# Phase 0 POC: MediaMTX + WebRTC (WHIP/WHEP)

- 브랜치: `feature/poc-mediamtx-webrtc`
- POC 코드: [`poc/mediamtx-webrtc/`](../../poc/mediamtx-webrtc/)
- 작성일: 2026-11-24 (PC 실기기 결과 반영: 2026-11-24)
- 상태: **PC 대상 Phase 0 POC 완료. 모바일 실기기 검증은 보류.** (요약: 0장, 상세: 7장)

## 0. 검증 범위 요약

| 구분 | 내용 | 근거 |
| --- | --- | --- |
| **검증됨** | MediaMTX·Caddy Docker 실행과 설정 (POC-01) | `scripts/smoke.sh` |
| **검증됨** | Windows PC Chrome에서 화면 공유 송출, 마이크 송출, 탭·시스템 오디오 송출 | PC 실기기 PC-02~04 |
| **검증됨** | 같은 PC의 다른 Chrome 탭에서 WHEP로 영상·음성 시청 | PC 실기기 PC-05 |
| **검증됨** | 정상 송출 종료와 시청 쪽 수신 중단 감지 (감지 시간은 **미측정**) | PC 실기기 PC-06 |
| **검증됨** | 같은 방송 이름으로 다시 송출·시청 | PC 실기기 PC-07 |
| **검증됨** | 송출 탭을 닫은 뒤 다시 송출했을 때 시청 쪽 재연결 | PC 실기기 PC-08 |
| **검증됨** | Caddy 내부 루트 CA를 Windows에 등록한 뒤 Chrome에서 `https://172.30.1.51:8443/` 경고 없이 접속 | 사용자 확인 |
| 검증 안 됨 | 다른 기기(스마트폰 등)에서 LAN IP로 접속, Wi-Fi·방화벽을 지나는 WebRTC 미디어 경로 | 스마트폰 접속 실패, 미해결 (10장 문제 7) |
| 검증 안 됨 | 모바일 카메라·마이크 송출, PC ↔ 모바일 시청 (POC-05~08) | 보류 |
| 검증 안 됨 | 실제 네트워크 단절 (POC-10), 재연결 유예 시간 정책(예: 180초) | 미실행. PC-08은 송출 탭 종료만 확인했다 |
| 검증 안 됨 | Chrome 이외 브라우저(Edge, Firefox, Safari), 체감 지연 측정 | 미실행 |

이 문서에 나오는 `172.30.1.51`은 **2026-11-24 테스트를 수행한 PC의 실제 LAN IP**이며, 테스트 기록으로만 남긴다. 다른 환경에서 실행할 때는 4장 절차대로 자기 PC의 LAN IP를 `.env`의 `POC_LAN_IP`에 넣고, 문서의 `<POC_LAN_IP>` 자리에 그 값을 쓴다.

PC 실기기 결과는 **송출과 시청이 같은 PC**에서 나온 것이다. 따라서 Wi-Fi를 통한 기기 간 전송, Windows 방화벽, Docker Desktop UDP 포워딩이 외부 기기에 미치는 영향(10장 문제 2)은 아직 확인되지 않았다.

## 1. 목적과 범위

Spring Boot와 비즈니스 모듈을 구현하기 전에, 실시간 방송의 핵심 기술(MediaMTX, WebRTC, WHIP 송출, WHEP 시청, 브라우저 MediaDevices API, Docker)이 실제로 동작하는지 확인한다.

| 포함 | 제외 (후속 과제, 12장) |
| --- | --- |
| 같은 Wi-Fi(LAN) 안의 PC·모바일 송출/시청 | STUN/TURN, LTE·5G 외부망 |
| Caddy `tls internal` HTTPS | 공인 인증서, 도메인 |
| 인증 없는 WHIP 송출 (신뢰할 수 있는 LAN 한정) | 송출 인증·권한 검증 |

## 2. 결정 사항

2026-11-24 사용자 결정 (정책 인터뷰 Q1~Q4).

| ID | 결정 | 이유 |
| --- | --- | --- |
| Q1 | POC 파일은 모두 `poc/mediamtx-webrtc/` 안에 둔다. `infrastructure/`, `backend/`, `frontend/`는 바꾸지 않는다. | "미디어 서버만 독립 구성" 요구를 따르고, 검증 전 설정이 실서비스 경로에 섞이지 않게 한다. |
| Q2 | 모바일 HTTPS는 POC 전용 Caddy 컨테이너 + `tls internal`로 만든다. | 페이지 제공과 WHIP/WHEP 프록시를 한 origin에서 처리하고, 저장소의 Caddy 방향과 맞춘다. |
| Q3 | 같은 Wi-Fi(LAN)만 검증한다. STUN/TURN과 외부망은 후속 과제로 둔다. | POC 목표(핵심 기술 동작 확인)에 맞춘다. |
| Q4 | WHIP 송출 인증 없이 신뢰할 수 있는 LAN에서만 쓴다. 포트 포워딩·외부 공개를 하지 않는다. Control API(9997)는 localhost 전용이다. | 인증 구조는 Streaming 모듈 구현 때 정한다. |

## 3. 구성

### 3.1 버전

| 구성 요소 | 이미지 태그 | 확인 근거 |
| --- | --- | --- |
| MediaMTX | `bluenviron/mediamtx:1.21.4` | GitHub 최신 안정 릴리스 v1.21.4 (2026-11-15 배포, prerelease 아님). 컨테이너 로그 `MediaMTX v1.21.4, linux, amd64` |
| Caddy | `caddy:2.11.9-alpine` | GitHub 최신 릴리스 v2.11.9 (2026-11-05 배포) |

`latest` 태그는 쓰지 않는다. 버전을 바꾸면 `docker-compose.yml`, `scripts/smoke.sh`의 기대값, 이 표를 함께 고친다.

### 3.2 파일

| 파일 | 내용 |
| --- | --- |
| `docker-compose.yml` | MediaMTX·Caddy 서비스, 포트, 고정 서브넷 `10.250.0.0/24`, Caddy 데이터 볼륨 |
| `mediamtx.yml` | WebRTC만 켜고 RTSP·RTMP·HLS·SRT·MoQ는 끔. 인증·API·ICE 설정. 경로는 `live/<이름>`만 허용 |
| `Caddyfile` | `tls internal` HTTPS(8443), `/live/*` → MediaMTX 프록시, `/ca/root.crt` 루트 인증서 제공, 정적 페이지 |
| `.env.example` | `POC_LAN_IP` 예시. 복사한 `.env`는 커밋하지 않음 |
| `web/index.html` | 페이지 목록, 루트 인증서 다운로드, 브라우저 지원 여부 표시 |
| `web/publish-pc.html` | PC 송출: 화면 공유 + 마이크 + 화면 오디오 믹싱(Web Audio) → WHIP |
| `web/publish-mobile.html` | 모바일 송출: 전면·후면 카메라(송출 중 전환 가능) + 마이크 → WHIP |
| `web/play.html` | WHEP 시청, 수신 통계, 자동 재연결, 수신 정지 감지 |
| `web/js/common.js` | WHIP/WHEP 협상, 세션 DELETE, 코덱 우선순위, 통계, 로그 |
| `scripts/smoke.sh` | POC-01 자동 확인 |
| `.gitattributes` | `*.sh text eol=lf`. `core.autocrlf=true`인 Windows에서 checkout해도 셸 스크립트가 CRLF로 바뀌어 bash에서 실패하지 않게 한다 |

### 3.3 네트워크와 포트

```
 PC/모바일 브라우저 (같은 Wi-Fi)
   │  ① HTTPS 페이지 + WHIP/WHEP 시그널링        https://<POC_LAN_IP>:8443
   ▼
 Caddy (tls internal) ── /live/* ──▶ MediaMTX :8889 (HTTP, Docker 네트워크 안)

 PC/모바일 브라우저
   │  ② WebRTC 미디어 (SRTP/ICE, UDP) ─ Caddy를 거치지 않음 ─▶ <POC_LAN_IP>:8189/udp ▶ MediaMTX
```

HTTPS(①)와 미디어(②)는 서로 다른 경로다. 페이지가 열리고 WHIP/WHEP가 201을 돌려줘도, 8189/udp가 막혀 있으면 영상은 나오지 않는다(연결 상태가 `failed`로 바뀐다).

| 호스트 포트 | 바인딩 | 용도 |
| --- | --- | --- |
| 8443/tcp | 모든 인터페이스 | Caddy HTTPS (페이지, WHIP/WHEP) |
| 8189/udp | 모든 인터페이스 | WebRTC 미디어 (ICE) |
| 8889/tcp | `127.0.0.1` | MediaMTX WHIP/WHEP 직접 호출 (PC 내부 디버깅용) |
| 9997/tcp | `127.0.0.1` | MediaMTX Control API |

주요 MediaMTX 설정:

- `webrtcAdditionalHosts`: `.env`의 `POC_LAN_IP`. Compose 환경변수 `MTX_WEBRTCADDITIONALHOSTS`로 넣는다.
- `webrtcIPsFromInterfaces: false`: 컨테이너 내부 IP(10.250.0.x)는 다른 기기가 닿을 수 없으므로 알리지 않는다.
- `webrtcICEServers2: []`: STUN/TURN을 쓰지 않는다(Q3).
- `webrtcTrustedProxies: [10.250.0.0/24]`: 로그에 Caddy가 아니라 실제 클라이언트 IP가 남도록 한다.
- API 권한: `127.0.0.1`, `::1`, `10.250.0.0/24`만 허용한다. 호스트에서 `127.0.0.1:9997`로 들어온 요청은 컨테이너 안에서 Docker 게이트웨이 `10.250.0.1`로 보인다(실측).

## 4. 실행 방법

```bash
cd poc/mediamtx-webrtc
cp .env.example .env          # POC_LAN_IP를 이 PC의 LAN IP로 바꾼다 (Windows: ipconfig → Wi-Fi IPv4 주소)
docker compose up -d
bash scripts/smoke.sh         # POC-01 자동 확인 (Git Bash)
docker compose logs -f mediamtx              # 세션 생성·종료 로그
curl -s http://127.0.0.1:9997/v3/paths/list  # 송출 중인 경로 상태 (ready, tracks)
docker compose down           # 종료 (Caddy 볼륨은 남아 루트 인증서가 유지됨)
```

- 접속 주소: `https://<POC_LAN_IP>:8443/` (PC와 모바일 모두 같은 주소)
- LAN IP가 바뀌면 `.env`를 고치고 `docker compose up -d`를 다시 실행한다. Caddy가 새 IP로 인증서를 다시 발급하고, 루트 인증서는 그대로라 기기에 다시 설치하지 않아도 된다.
- `docker compose down -v`로 볼륨까지 지우면 루트 인증서가 새로 만들어지므로 기기에 다시 설치해야 한다.

### 4.1 Windows 방화벽 (실기기 검증 전 확인)

2026-11-24 확인 결과, 이 PC의 Wi-Fi 네트워크 프로필은 **Public**이다. Public 프로필은 보통 LAN 수신 연결을 막는다. 방화벽 규칙은 관리자 권한이 없어 조회하지 못했다(`Get-NetFirewallRule: Access is denied`). 아래 명령은 실행하지 않았다. 필요하면 사용자가 **관리자 PowerShell**에서 직접 실행한다.

```powershell
# 현재 네트워크 프로필 확인
Get-NetConnectionProfile

# 방법 A (권장): 신뢰하는 집/사무실 Wi-Fi라면 Private으로 바꾸고, Private 프로필에만 허용 규칙을 둔다
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private
New-NetFirewallRule -DisplayName "YHS POC MediaMTX UDP 8189" -Direction Inbound -Protocol UDP -LocalPort 8189 -Action Allow -Profile Private
New-NetFirewallRule -DisplayName "YHS POC Caddy TCP 8443"   -Direction Inbound -Protocol TCP -LocalPort 8443 -Action Allow -Profile Private

# POC가 끝나면 규칙 삭제
Remove-NetFirewallRule -DisplayName "YHS POC MediaMTX UDP 8189", "YHS POC Caddy TCP 8443"
```

확인 방법: 모바일에서 `https://<POC_LAN_IP>:8443/`이 열리면 8443은 열린 것이다. 시청 페이지가 `ICE 상태: checking` 뒤에 `failed`가 되면 8189/udp가 막혔을 가능성이 크다.

## 5. HTTPS와 인증서 신뢰 절차

Caddy는 시작할 때 자체 내부 CA(`Caddy Local Authority - <연도> ECC Root`, 10년 유효)를 만들고, 그 CA로 `POC_LAN_IP`와 `localhost` 인증서를 발급한다. 테스트 기기가 이 루트 인증서를 신뢰해야 경고 없이 접속된다.

> 보안 주의: 루트 인증서를 신뢰한 기기는 이 CA로 서명한 모든 인증서를 믿는다. CA 개인 키는 Caddy 볼륨(`caddy_data`)에만 있고 웹으로 제공하지 않는다(`/ca/root.key` → 404, smoke 확인). **POC가 끝나면 테스트 기기에서 인증서를 삭제한다.**

### 5.1 루트 인증서 받기

- 기기 브라우저에서 `https://<POC_LAN_IP>:8443/ca/root.crt`를 연다. 처음에는 인증서 경고가 뜨므로 "고급 → 계속"으로 진행한다.
- 또는 PC에서 추출: `docker compose cp caddy:/data/caddy/pki/authorities/local/root.crt ./yhs-poc-caddy-root.crt`
- 받은 인증서가 맞는지 **인증서 지문**을 비교한다. 비교 기준은 **자기 환경에서 실행한 `scripts/smoke.sh` 출력의 SHA-256 값**이다. 9.1의 지문은 2026-11-24 테스트 환경의 값이므로, Caddy 볼륨이 다른 환경이나 볼륨을 다시 만든 환경에서는 일치하지 않는다.
  - openssl: `openssl x509 -in yhs-poc-caddy-root.crt -noout -fingerprint -sha256`
  - Windows PowerShell:
    ```powershell
    $c = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2 "$env:USERPROFILE\Downloads\yhs-poc-caddy-root.crt"
    $c.GetCertHashString([System.Security.Cryptography.HashAlgorithmName]::SHA256)  # 인증서 SHA-256 지문
    $c.Thumbprint                                                                  # SHA-1 Thumbprint (Windows 설치 창·저장소에 표시되는 값)
    ```

#### 인증서 지문과 파일 해시는 다르다

2026-11-24 확인 과정에서, 같은 `root.crt` 파일에 대해 서로 다른 SHA-256 값 두 개가 나왔다. 루트 CA가 바뀐 것이 아니라 계산 대상이 달랐다.

| 값 | 계산 대상 | 구하는 방법 | 비교에 쓰는가 |
| --- | --- | --- | --- |
| **인증서 SHA-256 지문** | 인증서 자체(DER 인코딩) | `openssl x509 -fingerprint -sha256`, .NET `GetCertHashString(SHA256)`, 기기의 인증서 상세 화면 | **쓴다** |
| **Thumbprint (SHA-1)** | 인증서 자체(DER 인코딩) | .NET `Thumbprint`, Windows 설치 경고 창, `Cert:\` 저장소 | 쓴다 (Windows) |
| PEM 파일 SHA-256 해시 | `.crt` 파일의 텍스트 바이트(헤더·줄바꿈 포함) | `Get-FileHash`, `sha256sum` | **쓰지 않는다**. 같은 인증서도 PEM/DER 형식이나 줄바꿈이 바뀌면 값이 달라진다 |

당시 확인 내용: 컨테이너의 `root.crt`와 사용자가 추출한 파일은 바이트가 같았다(일련번호 `33787DACF80FDC6C8BE0CDD7B53ED1A0`). 볼륨과 CA 파일도 처음 만든 뒤 바뀌지 않았다. 서버 인증서 체인은 이 루트로 검증되었다(`Verify return code: 0`, 중간 CA의 발급자 키 ID와 루트의 키 ID 일치).

### 5.2 기기별 설치

메뉴 이름은 OS 버전과 제조사에 따라 다를 수 있다. 실제로 설치해 본 기기와 메뉴 경로는 9장에 기록한다.

| 기기 | 설치 | 삭제 |
| --- | --- | --- |
| Windows PC (Chrome/Edge) | 지문 확인 뒤 `certutil -user -addstore Root yhs-poc-caddy-root.crt` (현재 사용자 저장소, 관리자 권한 불필요). 경고 창의 Thumbprint가 9.1 값과 같을 때만 [예]. 중간 인증서는 설치하지 않는다 | `certutil -user -delstore Root <Thumbprint>` 또는 `certmgr.msc` → 신뢰할 수 있는 루트 인증 기관 → "Caddy Local Authority" 삭제 |
| Firefox (PC) | 자체 저장소를 쓴다. 설정 → 개인 정보 및 보안 → 인증서 보기 → 인증 기관 → 가져오기 | 같은 화면에서 삭제 |
| Android (Chrome) | 설정 → 보안(및 개인정보 보호) → 기타 보안 설정 / 암호화 및 사용자 인증 정보 → 인증서 설치 → **CA 인증서** → 파일 선택 | 같은 메뉴 → 사용자 인증서 → 삭제 |
| iOS / iPadOS (Safari) | ① Safari로 다운로드 → "프로파일이 다운로드됨" ② 설정 → 일반 → VPN 및 기기 관리 → 프로파일 설치 ③ 설정 → 일반 → 정보 → **인증서 신뢰 설정** → 해당 루트 "전체 신뢰" 켜기 | 설정 → 일반 → VPN 및 기기 관리 → 프로파일 제거 |

### 5.3 신뢰 확인

0. (Windows) 저장소 등록과 OS 수준 HTTPS 검증:
   ```powershell
   Get-ChildItem Cert:\CurrentUser\Root\<Thumbprint> | Format-List Subject, NotAfter
   curl.exe -sS -o NUL -w "%{http_code}`n" https://<POC_LAN_IP>:8443/   # -k 없이 200 (Windows curl.exe는 Windows 저장소 사용)
   ```
1. 기기 브라우저에서 `https://<POC_LAN_IP>:8443/`을 새로 연다(경고 예외를 저장했다면 브라우저를 완전히 껐다 켠다).
2. 주소창에 인증서 경고가 없어야 한다.
3. 페이지의 "이 브라우저의 지원 여부"에 `✔ 보안 컨텍스트(isSecureContext)`가 표시되어야 한다.

경고를 무시하고 진행해도 보안 컨텍스트는 생기지만, 그 상태는 "인증서 신뢰"로 기록하지 않는다.

## 6. WHIP / WHEP 요청 방식

구현: `web/js/common.js` (`negotiate`, `deleteSession`). 페이지와 엔드포인트는 같은 origin이므로 CORS가 필요 없다.

| 단계 | 요청 | 응답 (실측) |
| --- | --- | --- |
| 송출 시작 (WHIP) | `POST /live/<이름>/whip`, `Content-Type: application/sdp`, 본문 = SDP offer | `201 Created`, `Location: /live/<이름>/whip/<세션 UUID>`, 본문 = SDP answer |
| 시청 시작 (WHEP) | `POST /live/<이름>/whep` (recvonly offer) | `201 Created`, `Location: /live/<이름>/whep/<세션 UUID>` |
| 종료 | `DELETE <Location>` | `200` |
| 엔드포인트 확인 | `OPTIONS /live/<이름>/whip` | `204` (`Link` 헤더에 ICE 서버. 이번 설정에서는 없음) |
| 송출 없는 경로 시청 | `POST /live/<없는 이름>/whep` | `404` → 시청 페이지가 2초마다 재시도 |
| 허용하지 않은 경로 | `POST /notallowed/whip`, `POST /live/a/b/whip` | `400` `path '...' is not configured` |

- **ICE 방식**: trickle ICE(PATCH)를 쓰지 않는다. 후보 수집이 끝날 때까지(최대 3초) 기다린 뒤 offer를 한 번에 보낸다. LAN에서는 수집이 빨라 문제가 없었다.
- **코덱**: 송출 페이지 기본값은 H264(선택 가능: VP8, 브라우저 기본). 오디오는 Opus. Safari를 포함해 시청 호환성을 넓히려고 H264를 기본으로 했다.
- **세션 URL**: MediaMTX가 돌려주는 `Location`은 절대 경로다. 그래서 Caddy가 `/live/*`를 경로를 바꾸지 않고 그대로 프록시해야 DELETE가 맞는 곳으로 간다.
- **페이지를 닫을 때**: `pagehide`에서 `fetch(DELETE, { keepalive: true })`로 세션을 정리한다.

## 7. 검증 결과

판정: **PASS** = 실제 실행 후 기준 충족 / **FAIL** = 실행했으나 실패 / **미검증** = 실행하지 않음 / **보류** = 모바일 실기기 검증 보류 결정에 따라 실행하지 않음.

POC-01~10 번호는 계획서(2.5) 그대로 유지한다. PC 실기기 테스트는 별도 번호(PC-02~08)로 수행했으며, 대응 관계는 7.2에 있다.

| ID | 검증 | 완료 기준 | 결과 | 근거 |
| --- | --- | --- | --- | --- |
| POC-01 | MediaMTX | Docker 정상 실행 | **PASS** | `scripts/smoke.sh` 35/35 통과 (9.3) |
| POC-02 | PC 화면 공유 | 영상 송출 성공 | **PASS** | PC-02 (Windows Chrome) |
| POC-03 | PC 마이크 | 음성 전달 성공 | **PASS** | PC-03 (Windows Chrome) |
| POC-04 | 화면 오디오 | 지원 브라우저에서 검증 | **PASS** (Windows Chrome) | PC-04 (탭·시스템 오디오). 다른 브라우저는 미검증 |
| POC-05 | PC → 모바일 | 모바일 실시간 시청 | **보류** | 스마트폰이 LAN IP에 접속하지 못함 (10장 문제 7). PC-05는 같은 PC의 다른 탭 시청이므로 POC-05를 대신하지 않는다 |
| POC-06 | 모바일 카메라 | 영상 송출 성공 | **보류** | 모바일 검증 보류 |
| POC-07 | 모바일 마이크 | 음성 전달 성공 | **보류** | 모바일 검증 보류 |
| POC-08 | 모바일 → PC | PC 실시간 시청 | **보류** | 모바일 검증 보류 |
| POC-09 | 송출 종료 | 연결 종료 확인 | **PASS** (PC 송출 범위) | PC-06. 시청 쪽 수신 중단 감지 시간은 **미측정**. 모바일 송출 종료는 보류 |
| POC-10 | 네트워크 단절 | MediaMTX 상태 변화 확인 | 미검증 | 실제 네트워크 단절은 실행하지 않았다. PC-08(송출 탭 종료)은 네트워크 단절 검증이 아니다 |

### 7.1 POC-01 자동 확인 범위 (`scripts/smoke.sh`)

이 스크립트는 **서버가 준비되었는지만** 확인한다. WHIP/WHEP 엔드포인트가 응답해도 송출·재생 성공으로 판정하지 않는다.

1. 컨테이너: 두 서비스 running, 이미지 태그, 로그의 MediaMTX 버전, MediaMTX 로그에 ERR 없음
2. Control API: localhost 응답, WebRTC on, RTSP/RTMP/HLS/SRT off, `webrtcIPsFromInterfaces=false`, `webrtcAdditionalHosts=[POC_LAN_IP]`, ICE 서버 없음, LAN IP:9997로는 접근 불가
3. 포트: 8189/udp 전체 바인딩, 8889·9997은 127.0.0.1 바인딩
4. HTTPS: 내부 루트 CA 추출, `openssl s_client -verify_ip`로 LAN IP 인증서 체인 검증, 페이지 200, `/ca/root.crt`가 루트 CA와 같음, `/ca/root.key` 미노출
5. 엔드포인트: Caddy 경유 WHIP/WHEP OPTIONS 204, 송출 없는 WHEP 404, 허용하지 않은 경로 거부

검증이 실제로 실패를 잡는지 확인: 같은 인증서를 다른 IP로 검증하면 `IP address mismatch`, CA 없이 검증하면 `unable to get local issuer certificate`로 실패했다.

### 7.2 PC 실기기 테스트 결과 (사용자 수행, 2026-11-24 보고)

- 환경: Windows PC 1대, Chrome. **송출과 시청을 같은 PC의 Chrome 탭에서** 수행했다. Chrome 버전은 기록하지 않았다.
- 접속 주소: `https://172.30.1.51:8443/`. Caddy 내부 루트 CA를 Windows 신뢰할 수 있는 루트 인증 기관에 등록한 뒤, Chrome에서 인증서 경고 없이 접속했다(5장).
- 결과: **7개 항목 모두 성공, 특이사항 없음** (사용자 보고).

| PC 테스트 ID | 내용 | 결과 | 대응 POC ID | 비고 |
| --- | --- | --- | --- | --- |
| PC-02 | 화면 공유 영상 송출 | 성공 | POC-02 | |
| PC-03 | 마이크 음성 송출 | 성공 | POC-03 | |
| PC-04 | 탭·시스템 오디오 송출 | 성공 | POC-04 | Windows Chrome 기준 |
| PC-05 | 다른 탭에서 영상·음성 시청 | 성공 | (POC-02·03의 시청 확인) | 같은 PC 시청. POC-05(PC → 모바일)와는 다르다 |
| PC-06 | 정상 송출 종료와 수신 중단 감지 | 성공 | POC-09 (PC 송출 범위) | 감지 시간 **미측정**. 8.2의 3.8~4.8초는 headless 보조 확인 값이며 실기기 측정값이 아니다 |
| PC-07 | 같은 방송 이름으로 다시 송출·시청 | 성공 | 대응 없음 (추가 항목) | |
| PC-08 | 송출 탭 종료 뒤 재연결 동작 | 성공 | 대응 없음 (추가 항목) | 송출 탭을 닫은 경우만 확인했다. **실제 네트워크 장애(POC-10)와 재연결 유예 시간 정책(예: 180초)은 검증하지 않았다.** POC 코드에는 유예 시간 정책이 없고, 시청 페이지는 2초 간격 재시도와 4초 수신 정지 감지만 한다 |

PC 테스트 목록은 보고받은 대로 PC-02부터 적었다(PC-01은 보고에 없음).

## 8. 실기기 테스트 절차

2026-11-24 기준 PC 항목(POC-02~04, POC-09의 PC 송출)은 7.2 결과로 완료했다. 모바일 항목(POC-05~08, POC-09의 모바일 송출)은 **보류**이며, 아래 절차는 재개할 때 쓴다. 재개 전에 10장 문제 7(스마트폰 LAN IP 접속 실패)을 먼저 해결해야 한다.

### 8.1 공통 준비

1. `docker compose up -d`, `bash scripts/smoke.sh`가 FAIL 0인지 확인한다.
2. 4.1의 방화벽 설정을 확인한다.
3. 각 기기에 루트 인증서를 설치하고 5.3으로 신뢰를 확인한다.
4. 테스트 중에는 PC에서 `docker compose logs -f mediamtx`를 띄워 둔다.
5. 각 페이지 아래의 "이 브라우저의 지원 여부"와 User-Agent를 9.2 표에 옮겨 적는다.
6. 실패하면 브라우저, 네트워크, 페이지 로그(오류 메시지), MediaMTX 로그, 재현 방법을 9.3에 적는다.

| ID | 절차 | 성공 기준 |
| --- | --- | --- |
| POC-02 | PC Chrome/Edge에서 `publish-pc.html` → ① 화면 선택(전체 화면, 창, 탭 각각) → ③ 송출 시작. 다른 브라우저나 기기에서 `play.html?stream=pc` | 시청 쪽에 화면이 움직이며 보임. 송출 통계 kbps > 0, 시청 통계 디코딩 프레임 증가 |
| POC-03 | ② 마이크 켜기 → 송출 중 말하기. 시청 쪽 "소리 켜기" | 시청 쪽에서 목소리가 들림. 시청 통계 audio 음량 값이 변함 |
| POC-04 | ① 화면 선택에서 **탭**(오디오 공유 체크) 또는 **전체 화면**(시스템 오디오 공유 체크) 선택 → 소리 나는 영상 재생 → 마이크와 함께 송출. Firefox·Safari에서도 시도 | 지원 브라우저에서 화면 소리와 목소리가 함께 들림. 미지원 브라우저는 "화면 오디오 없음"을 기록 |
| POC-05 | PC 송출 중 모바일에서 `play.html?stream=pc` → "소리 켜기" | 모바일에서 화면과 소리가 실시간으로 나옴. 체감 지연을 기록(예: PC 시계 화면 송출 후 비교) |
| POC-06 | 모바일에서 `publish-mobile.html` → ① 카메라 켜기(권한 허용) → ③ 송출 시작 → "전면/후면 전환" | PC 시청 쪽에 카메라 영상이 보이고, 전환 후에도 끊기지 않음 |
| POC-07 | ② 마이크 켜기(권한 허용) → 말하기 | PC 시청 쪽에서 목소리가 들림 |
| POC-08 | PC에서 `play.html?stream=mobile` | PC에서 모바일 영상과 소리가 실시간으로 나옴 |
| POC-09 | 송출 쪽 ④ 송출 중단(PC는 브라우저의 "공유 중지"로도 확인) | 송출 로그 `DELETE ... → 200`, MediaMTX 로그 `closed: terminated`, `/v3/paths/list`에서 경로 제거, 시청 쪽 "수신 멈춤" → "송출 없음". 카메라·마이크 표시등 꺼짐 |
| POC-10 | 모바일 송출 → PC 시청 중 모바일 Wi-Fi 끄기(비행기 모드). 10초 이상 기다린 뒤 다시 켜기. 반대로 PC 시청 중 PC 네트워크 끊기도 확인 | 시각 기록: Wi-Fi 끈 시각, 시청 쪽 "수신 멈춤" 시각, MediaMTX 세션 종료 로그 시각과 사유, `/v3/paths/list` ready 변화. 재연결 시 송출 쪽 동작 기록 |

### 8.2 보조 자동 확인 (실기기 검증을 대신하지 않음)

이 PC의 Chrome 151(headless)에서 **가짜 카메라·마이크·화면**(`--use-fake-device-for-media-stream`, `--use-fake-ui-for-media-stream`)으로 송출 페이지 → 시청 페이지 전체 흐름을 자동으로 돌렸다. 인증서는 `acceptInsecureCerts`로 무시했다. 송출과 시청이 같은 PC라 Wi-Fi와 방화벽 경로는 지나지 않는다. 스크립트는 저장소 밖(임시 폴더)에서 Puppeteer로 실행했고 저장소에 넣지 않았다.

| 시나리오 | 결과 |
| --- | --- |
| `publish-mobile.html` (가짜 카메라 1280x720 + 가짜 마이크) → `play.html` | WHIP connected, API 경로 ready `[H264, Opus]`, WHEP connected, 영상 1280x720 H264 디코딩(약 135프레임/7초), Opus 약 25kbps 수신, 첫 재생까지 483~724ms, DELETE 200, 경로 제거, 시청 쪽 4.8초 뒤 "수신 멈춤" |
| `publish-pc.html` (가짜 화면 800x450 + 화면 오디오 + 가짜 마이크, 믹싱) → `play.html` | WHIP connected, `[H264, Opus]`, 영상 800x450 H264 디코딩, Opus 약 33kbps 수신, 첫 재생 562ms, DELETE 200, 경로 제거, 시청 쪽 3.8초 뒤 "수신 멈춤" |
| 합계 | **16/16 통과** (최종 실행. 첫 두 번의 실행은 9장 참고) |

가짜 마이크는 짧은 비프음만 내므로 수신 통계의 음량 값은 대부분 0이었다. "들리는 소리"는 POC-03/07에서 사람이 확인해야 한다.

## 9. 테스트 환경과 실행 기록

### 9.1 서버 환경

| 항목 | 값 |
| --- | --- |
| OS | Windows 10 Pro 10.0.19045 |
| Docker | Docker 29.8.0, Compose v5.5.1 (Docker Desktop) |
| 네트워크 | Wi-Fi, LAN IP 172.30.1.51/24 (테스트 PC의 실제 값, 일반 절차에서는 `POC_LAN_IP`), 네트워크 프로필 Public |
| 이번 루트 CA | `CN=Caddy Local Authority - 2026 ECC Root`, 일련번호 `33787DACF80FDC6C8BE0CDD7B53ED1A0`, 만료 2036-10-02 (볼륨을 지우면 바뀜) |
| 루트 CA 인증서 SHA-256 지문 | `24202BD4D349DDDF438CEA02593F06C0BC81E5BF6A8B0122AA5CDF3D51EE1171` |
| 루트 CA Thumbprint (SHA-1) | `C6797A8F86873807A8B1BEB13AA6CF88DAAA1181` |
| (참고) PEM 파일 SHA-256 해시 | `8686318E6C024777EFFF23F8B3CA9AB62424CD93F68B93A6908B7736A41D68D9` — 지문 비교에 쓰지 않음 (5.1) |

> 위 루트 CA 값은 **2026-11-24 이 POC 환경의 Caddy 볼륨(`yhs-poc-mediamtx_caddy_data`)에서 발급된 공개 인증서**의 값이다. Caddy 볼륨을 다시 만들거나(`docker compose down -v` 등) 다른 PC에서 실행하면 새 루트 CA가 만들어져 지문이 달라진다. 그때는 그 환경의 `scripts/smoke.sh` 출력으로 비교한다. CA 개인 키(`root.key`, `intermediate.key`)는 볼륨에만 있으며 Git에 어떤 형태로도 넣지 않는다.
| PC 실기기 테스트 브라우저 | Chrome (버전 미기록), 루트 CA를 Windows 신뢰할 수 있는 루트 인증 기관에 등록 |

### 9.2 브라우저별 지원 여부

실제로 확인한 것만 적는다. 실기기 검증 때 행을 추가한다.

| 기기 / OS | 브라우저 | isSecureContext | getUserMedia | getDisplayMedia | 화면 오디오 | WHIP 송출 | WHEP 시청 | 인증서 신뢰 | 비고 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Windows 10 PC | Chrome 151 (headless, 가짜 장치) | ✔ | ✔ (가짜) | ✔ (가짜) | ✔ (가짜) | ✔ | ✔ | 미검증 (무시 설정) | 보조 자동 확인 (8.2) |
| Windows PC | Chrome (버전 미기록) | ✔ (HTTPS 경고 없이 접속) | ✔ (마이크) | ✔ | ✔ (탭·시스템 오디오) | ✔ | ✔ (같은 PC 다른 탭) | ✔ | PC 실기기 (7.2) |
| Windows PC | Edge | | | | | | | | 미검증 |
| (Android) | Chrome | | | | | | | | 보류 (LAN IP 접속 실패) |
| (iOS) | Safari | | | | | | | | 보류 |

### 9.3 실행 기록

| 일시 (KST) | 항목 | 결과 | 내용 |
| --- | --- | --- | --- |
| 2026-11-24 14:56 | `docker compose up -d` | 성공 | MediaMTX v1.21.4, WebRTC 리스너 :8889(TCP) :8189(UDP), API :9997. Caddy 172.30.1.51·localhost 인증서 발급 |
| 2026-11-24 14:57 | `scripts/smoke.sh` | PASS 35 / FAIL 0 | Caddy HTTP/3 비활성화 후 실행 |
| 2026-11-24 14:58 | 보조 자동 확인 1차 | 중단 | 송출·시청 성공. 송출 중단 단계에서 Puppeteer 시간 초과(뒤 탭 클릭 문제, 검증 스크립트 결함). ICE 후보쌍 판정 기준이 맞지 않아 관찰 항목으로 바꿈 |
| 2026-11-24 15:02 | 보조 자동 확인 2차 | PASS 14 / FAIL 2 | 송출 종료 뒤 시청 쪽이 30초 안에 상태가 바뀌지 않음 → 10장 문제 1 |
| 2026-11-24 15:04 | 보조 자동 확인 3차 | PASS 16 / FAIL 0 | 수신 정지 감지 추가 뒤 재실행 |
| 2026-11-24 15:05 | `scripts/smoke.sh` 재실행 | PASS 35 / FAIL 0 | 코드 수정 뒤 회귀 확인 |
| 2026-11-24 | 루트 CA 재확인 | 이상 없음 | 사용자가 계산한 SHA-256 값이 보고값과 달랐음 → PEM 파일 해시였음. 루트 CA·볼륨·체인은 바뀌지 않음 (5.1) |
| 2026-11-24 | 루트 CA 등록, Chrome HTTPS 접속 (사용자 수행) | 성공 | Windows 신뢰할 수 있는 루트 인증 기관에 등록 후 경고 없이 접속 |
| 2026-11-24 | PC 실기기 PC-02~08 (사용자 수행) | 7/7 성공 | 같은 PC에서 송출·시청. PC-06 감지 시간 미측정 (7.2) |
| 2026-11-24 | 스마트폰에서 LAN IP 접속 (사용자 수행) | 실패 | 원인 미확인 (10장 문제 7) |
| 2026-11-24 | 최종 검증: `scripts/smoke.sh`, `docker compose config`, `git diff --check`, 민감정보 검사 | PASS 35 / FAIL 0, 나머지 이상 없음 | PC 결과 문서 반영 후 |
| 2026-11-24 | `.gitattributes` 추가 후 검증: `git check-attr`, `git ls-files --eol`, `git checkout-index` 새 checkout 시뮬레이션, `scripts/smoke.sh`, `docker compose config`, `git diff --cached --check`, 민감정보 검사 | 이상 없음, smoke PASS 35 / FAIL 0 | `autocrlf=true`에서도 `smoke.sh` CR 0개 (LF 유지) |
| | POC-05~08 모바일, POC-10 | 보류 / 미검증 | |

## 10. 문제점과 해결

| # | 문제 | 원인 | 해결 / 현재 상태 |
| --- | --- | --- | --- |
| 1 | 송출을 멈춰도 시청 브라우저는 약 20초 동안 "재생 중"으로 남음 | MediaMTX는 송출 종료와 같은 초에 시청 세션을 `closed: terminated`로 닫지만, 브라우저는 ICE 응답이 끊긴 것(consent 만료)으로만 알아챔 | 시청 페이지에 수신 정지 감지 추가: 받은 바이트가 4초 동안 늘지 않으면 "수신 멈춤" 후 재연결. 감지 3.8~4.8초(보조 확인). 실제 서비스에서는 방송 종료 이벤트를 따로 알려야 함(11장) |
| 2 | ICE 원격 후보가 `<POC_LAN_IP>:8189 host`가 아니라 임의 포트의 `prflx`로 잡힘. MediaMTX 로그의 로컬 후보도 `10.250.0.2`나 `127.0.0.1`로 보임 | Docker Desktop(Windows)의 UDP 포트 포워딩이 주소와 포트를 바꿔서 전달하는 것으로 추정 | 같은 PC 안에서는 연결 성공. **LAN의 다른 기기에서도 되는지는 미검증** → POC-05/08에서 확인. 실패하면 Linux 호스트에서 `network_mode: host`, `webrtcLocalTCPAddress`(TCP ICE) 순서로 대안 검토 |
| 3 | Caddy가 HTTP/3(UDP 8443)를 광고하지만 그 포트는 열려 있지 않음 | Caddy 기본값 | `servers { protocols h1 h2 }`로 HTTP/3 끔 |
| 4 | 호스트에서 `127.0.0.1:9997` API를 호출하면 컨테이너는 출발지를 `10.250.0.1`로 봄 | Docker 포트 포워딩 | 고정 서브넷 `10.250.0.0/24`에 API 권한 부여. 호스트 바인딩은 `127.0.0.1`로 제한 |
| 5 | Wi-Fi 네트워크 프로필이 Public이라 LAN 수신이 막힐 수 있음 | Windows 설정 | 4.1에 확인·설정 명령 기록. 사용자 확인 필요 |
| 6 | 설정에 없는 경로에 `OPTIONS`를 보내면 500이 돌아옴 | MediaMTX v1.21.4 동작 | POST는 400 `path is not configured`로 거부되므로 송출은 막힘. smoke는 POST로 확인 |
| 7 | **스마트폰에서 `https://172.30.1.51:8443/`(LAN IP)에 접속하지 못함** | **미확인.** 오류 메시지와 스마트폰 네트워크 상태는 기록되지 않았다. 확인할 후보(추정): ① Windows 방화벽, Wi-Fi 프로필이 Public(4.1) ② 공유기의 무선 단말 격리(AP isolation)·게스트 Wi-Fi ③ 스마트폰이 같은 Wi-Fi가 아닌 LTE·5G·다른 SSID에 연결 ④ PC의 LAN IP 변경 | **미해결.** 모바일 검증 재개 전에 해결한다. 확인 순서 제안: 스마트폰의 Wi-Fi SSID와 IP 대역 → 같은 Wi-Fi의 다른 PC에서 접속 → 방화벽 규칙과 프로필(관리자 권한) → 공유기 격리 설정 |
| 8 | 루트 CA SHA-256 값이 보고값과 다르게 계산됨 | 인증서 지문 대신 PEM 파일 해시(`Get-FileHash`)를 계산함 | 해결. 5.1에 두 값의 차이와 올바른 확인 명령을 적음 |

## 11. 실제 프로젝트에 적용할 설정 (제안, 미확정)

POC 결과로 얻은 제안이다. 확정은 해당 모듈 설계 때 한다. 실기기 결과에 따라 바뀔 수 있다.

1. **버전 고정**: MediaMTX·Caddy 이미지를 패치 버전까지 고정한다.
2. **필요한 프로토콜만 켜기**: WebRTC만 쓰면 RTSP/RTMP/HLS/SRT/MoQ를 끈다. RTMP(OBS 송출) 지원 여부는 별도로 정한다.
3. **ICE 주소**: `webrtcIPsFromInterfaces: false`와 `webrtcAdditionalHosts`(서버 공인 IP 또는 도메인)를 명시한다. 외부망에서는 STUN/TURN(coturn)이 필요할 수 있다(후속 검증).
4. **시그널링과 미디어 분리**: HTTPS 리버스 프록시(Caddy)는 WHIP/WHEP 시그널링만 처리하고, 미디어 UDP 포트는 MediaMTX에 직접 연다. 방화벽·보안그룹에 두 경로를 모두 연다.
5. **API 보호**: Control API는 외부에 노출하지 않는다(내부 네트워크·localhost 바인딩 + 권한 IP 제한).
6. **경로 규칙**: 허용 경로를 정규식으로 제한한다(예: `live/<streamKey>`).
7. **송출 인증**: `authHTTPAddress`로 Spring Boot에 송출·시청 권한을 묻는 방식이 유력 후보다(Q4에 따라 Streaming 모듈에서 결정).
8. **방송 종료 알림**: 시청 클라이언트가 ICE 만료(약 20초)에 기대지 않도록, 백엔드가 MediaMTX 경로 훅(v1.21.4 기준 `runOnOnline`/`runOnOffline`, `runOnAvailable`/`runOnUnavailable`)이나 API로 종료를 감지해 WebSocket 등으로 알린다. 클라이언트의 수신 정지 감지는 보조 수단으로 둔다.
9. **코덱**: 시청 호환성을 위해 H264 우선 + Opus를 기본으로 검토한다(Safari·iOS 실기기 결과로 확정).

## 12. 미검증 항목과 후속 과제

- [x] POC-01 서버 실행 확인 (smoke)
- [x] PC 실기기 PC-02~08 (POC-02·03·04, POC-09의 PC 송출 범위)
- [x] Windows PC에 Caddy 내부 루트 CA 등록, Chrome HTTPS 접속
- [ ] PC-06 수신 중단 감지 시간 측정 (미측정)
- [ ] 스마트폰 LAN IP 접속 실패 원인 확인과 해결 (10장 문제 7) — **모바일 재개 전제 조건**
- [ ] (보류) POC-05~08 모바일 실기기 검증, POC-09의 모바일 송출 종료
- [ ] (보류) 모바일에서 Caddy 내부 CA 인증서 신뢰 (Android·iOS 각각)
- [ ] POC-10 실제 네트워크 단절 검증
- [ ] 재연결 유예 시간 정책(예: 180초) — POC에서 검증하지 않음. Streaming 모듈 정책으로 정하고 검증
- [ ] Docker Desktop UDP 포워딩 환경에서 LAN의 다른 기기 ICE 연결 (10장 문제 2)
- [ ] Windows 방화벽·네트워크 프로필 설정과 실제 규칙 확인 (관리자 권한 필요)
- [ ] 브라우저별 지원 여부 표 나머지 (Edge, Firefox, Safari, Android Chrome, iOS Safari), Chrome 버전 기록
- [ ] 체감 지연(glass-to-glass) 측정
- [ ] 후속: STUN/TURN, LTE·5G 외부망 연결 검증 (Q3)
- [ ] 후속: 송출 인증·권한 검증 (Streaming 모듈, Q4)
- [ ] 후속: 방송 종료 이벤트 전달 구조 (11장 8)
- [ ] POC 종료 뒤 테스트 기기의 루트 인증서와 방화벽 규칙 삭제
