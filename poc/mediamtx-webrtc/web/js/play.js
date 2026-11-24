// WHEP 시청: recvonly 트랜시버로 offer → WHEP POST → 재생
import {
  createLogger, endpointFor, negotiate, deleteSession,
  watchConnection, startStats, renderEnvironment,
} from './common.js';

const $ = (id) => document.getElementById(id);
const log = createLogger($('log'));
renderEnvironment($('env'));

const params = new URLSearchParams(location.search);
if (params.get('stream')) $('stream').value = params.get('stream');

const RETRY_MS = 2000;
// MediaMTX는 송출이 끝나면 시청 세션을 바로 닫지만, 브라우저는 ICE 응답이 끊긴 것(약 20초)으로만 알아챈다.
// 그래서 받은 바이트가 STALL_MS 동안 늘지 않으면 송출 종료 또는 네트워크 단절로 보고 재연결한다.
const STALL_MS = 4000;
const state = {
  pc: null, sessionUrl: null, stopStats: null, retryTimer: null, stallTimer: null, wanted: false, startedAt: 0,
};

// 수신 정지 감지: 연결된 상태에서 inbound-rtp 바이트가 늘지 않는지 1초마다 본다.
function startStallWatch(pc) {
  let lastBytes = -1;
  let lastChange = performance.now();
  state.stallTimer = setInterval(async () => {
    if (pc !== state.pc || pc.connectionState !== 'connected') return;
    let bytes = 0;
    (await pc.getStats()).forEach((s) => { if (s.type === 'inbound-rtp') bytes += s.bytesReceived ?? 0; });
    const now = performance.now();
    if (bytes !== lastBytes) {
      lastBytes = bytes;
      lastChange = now;
    } else if (now - lastChange >= STALL_MS) {
      log(`${STALL_MS / 1000}초 동안 받은 데이터 없음 → 송출 종료 또는 네트워크 단절로 판단 (ICE 상태: ${pc.iceConnectionState})`, 'warn');
      setStatus('수신 멈춤', 'error');
      deleteSession(state.sessionUrl, log); // 서버가 이미 세션을 닫았으면 404가 기록된다
      state.sessionUrl = null;
      teardown();
      scheduleRetry('수신 멈춤');
    }
  }, 1000);
}

function setStatus(text, cls = '') {
  $('status').textContent = text;
  $('status').className = `status ${cls}`;
}

function scheduleRetry(reason) {
  if (!state.wanted || !$('autoRetry').checked) return;
  clearTimeout(state.retryTimer);
  log(`${reason} → ${RETRY_MS / 1000}초 뒤 다시 연결`, 'warn');
  state.retryTimer = setTimeout(connect, RETRY_MS);
}

function teardown() {
  clearInterval(state.stallTimer);
  state.stallTimer = null;
  state.stopStats?.();
  state.stopStats = null;
  state.pc?.close();
  state.pc = null;
}

async function connect() {
  teardown();
  let endpoint;
  try {
    endpoint = endpointFor($('stream').value.trim(), 'whep');
  } catch (e) {
    log(e.message, 'error');
    return;
  }
  setStatus('연결 중');
  state.startedAt = performance.now();
  const pc = new RTCPeerConnection(); // 결정 Q3=a: LAN 전용, ICE 서버 없음
  state.pc = pc;
  pc.addTransceiver('video', { direction: 'recvonly' });
  pc.addTransceiver('audio', { direction: 'recvonly' });

  const remote = new MediaStream();
  pc.addEventListener('track', (ev) => {
    remote.addTrack(ev.track);
    log(`수신 트랙: ${ev.track.kind}`);
    $('video').srcObject = remote;
  });
  watchConnection(pc, log, (s) => {
    if (pc !== state.pc) return; // 이전 연결의 이벤트는 무시
    if (s === 'connected') setStatus('재생 중', 'live');
    else if (s === 'disconnected') setStatus('연결 끊김(복구 대기)', 'error');
    else if (s === 'failed') {
      setStatus('연결 실패', 'error');
      scheduleRetry('연결 실패');
    }
  });

  try {
    state.sessionUrl = await negotiate(pc, endpoint, log);
    state.stopStats = startStats(pc, 'in', $('stats'));
    startStallWatch(pc);
  } catch (e) {
    teardown();
    if (e.status === 404) {
      setStatus('송출 없음');
      log('아직 송출 중인 스트림이 없습니다(404).', 'warn');
      scheduleRetry('송출 대기');
    } else {
      setStatus('실패', 'error');
      log(`WHEP 시청 실패: ${e.message}`, 'error');
      scheduleRetry('시청 실패');
    }
  }
}

$('video').addEventListener('playing', () => {
  const ms = Math.round(performance.now() - state.startedAt);
  log(`첫 재생 시작 (시청 요청부터 ${ms}ms)`);
});

$('btnPlay').onclick = () => {
  state.wanted = true;
  $('btnPlay').disabled = true;
  $('btnStop').disabled = false;
  connect();
};

$('btnStop').onclick = async () => {
  state.wanted = false;
  clearTimeout(state.retryTimer);
  await deleteSession(state.sessionUrl, log);
  state.sessionUrl = null;
  teardown();
  $('video').srcObject = null;
  setStatus('중지됨');
  $('btnPlay').disabled = false;
  $('btnStop').disabled = true;
};

$('btnUnmute').onclick = () => {
  const v = $('video');
  v.muted = !v.muted;
  v.play().catch(() => {});
  $('btnUnmute').textContent = v.muted ? '소리 켜기' : '소리 끄기';
};

window.addEventListener('pagehide', () => {
  if (state.sessionUrl) fetch(state.sessionUrl, { method: 'DELETE', keepalive: true });
});

// ?autoplay=1 이면 바로 시청을 시작한다(모바일에서 주소만 열어 확인할 때 편하다).
if (params.get('autoplay') === '1') $('btnPlay').click();
