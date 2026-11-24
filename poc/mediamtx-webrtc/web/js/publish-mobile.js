// 모바일 송출: getUserMedia(카메라, 마이크를 따로 요청) → WHIP
import {
  createLogger, endpointFor, negotiate, deleteSession, preferVideoCodec,
  watchConnection, startStats, renderEnvironment, startLevelMeter, describeTrack,
} from './common.js';

const $ = (id) => document.getElementById(id);
const log = createLogger($('log'));
renderEnvironment($('env'));

const state = {
  videoTrack: null,
  audioTrack: null,
  audioCtx: null,
  stopMeter: null,
  pc: null,
  videoSender: null, // 송출 중 카메라 전환(replaceTrack)에 쓴다
  audioSender: null, // 송출 뒤 마이크를 켤 때 쓴다
  sessionUrl: null,
  stopStats: null,
};

function setStatus(text, cls = '') {
  $('status').textContent = text;
  $('status').className = `status ${cls}`;
}

function renderTracks() {
  const ul = $('tracks');
  ul.textContent = '';
  [state.videoTrack, state.audioTrack].filter(Boolean).forEach((t) => {
    const li = document.createElement('li');
    li.textContent = describeTrack(t);
    ul.append(li);
  });
}

function videoConstraints() {
  const [w, h] = $('resolution').value.split('x').map(Number);
  return {
    facingMode: { ideal: $('facing').value },
    width: { ideal: w },
    height: { ideal: h },
    frameRate: { ideal: 30, max: 30 },
  };
}

function permissionHint(e) {
  if (e.name === 'NotAllowedError') return ' → 브라우저·OS 설정에서 카메라/마이크 권한을 허용했는지 확인하세요.';
  if (e.name === 'NotReadableError') return ' → 다른 앱이 카메라/마이크를 쓰고 있지 않은지 확인하세요.';
  if (e.name === 'OverconstrainedError') return ' → 해상도를 낮춰 보세요.';
  if (!window.isSecureContext) return ' → HTTPS(보안 컨텍스트)가 아닙니다.';
  return '';
}

// 카메라를 연다. 모바일은 카메라 두 개를 동시에 못 여는 경우가 많아, 기존 트랙을 먼저 멈춘다.
async function openCamera() {
  state.videoTrack?.stop();
  const stream = await navigator.mediaDevices.getUserMedia({ video: videoConstraints(), audio: false });
  const track = stream.getVideoTracks()[0];
  state.videoTrack = track;
  $('preview').srcObject = new MediaStream([track]);
  // 전면 카메라 미리보기는 거울처럼 보여 준다(송출 영상은 뒤집지 않는다).
  $('preview').style.transform = track.getSettings().facingMode === 'user' ? 'scaleX(-1)' : '';
  track.addEventListener('ended', () => log('카메라 트랙이 끝났습니다(OS가 회수했거나 권한 취소).', 'warn'));
  if (state.videoSender) {
    await state.videoSender.replaceTrack(track);
    log('송출 중 카메라 트랙 교체(replaceTrack, 재협상 없음)');
  }
  log(`카메라 켜짐: ${describeTrack(track)}`);
  renderTracks();
}

$('btnCam').onclick = async () => {
  try {
    await openCamera();
  } catch (e) {
    log(`카메라 실패: ${e.name} ${e.message}${permissionHint(e)}`, 'error');
  }
};

$('btnMic').onclick = async () => {
  try {
    state.audioTrack?.stop();
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      video: false,
    });
    state.audioTrack = stream.getAudioTracks()[0];
    const Ctx = window.AudioContext ?? window.webkitAudioContext;
    state.audioCtx ??= new Ctx();
    await state.audioCtx.resume();
    state.stopMeter?.();
    state.stopMeter = startLevelMeter(state.audioCtx, state.audioCtx.createMediaStreamSource(stream), $('meter'));
    if (state.audioSender) await state.audioSender.replaceTrack(state.audioTrack);
    log(`마이크 켜짐: ${describeTrack(state.audioTrack)}`);
    renderTracks();
  } catch (e) {
    log(`마이크 실패: ${e.name} ${e.message}${permissionHint(e)}`, 'error');
  }
};

$('btnSwitch').onclick = async () => {
  $('facing').value = $('facing').value === 'user' ? 'environment' : 'user';
  try {
    await openCamera();
  } catch (e) {
    log(`카메라 전환 실패: ${e.name} ${e.message}${permissionHint(e)}`, 'error');
  }
};

$('stream').oninput = (e) => {
  const href = `play.html?stream=${encodeURIComponent(e.target.value)}`;
  $('playLink').href = href;
  $('playLink').textContent = href;
};

$('btnStart').onclick = async () => {
  if (!state.videoTrack || state.videoTrack.readyState !== 'live') {
    log('먼저 ① 카메라를 켜세요.', 'warn');
    return;
  }
  let endpoint;
  try {
    endpoint = endpointFor($('stream').value.trim(), 'whip');
  } catch (e) {
    log(e.message, 'error');
    return;
  }
  $('btnStart').disabled = true;
  setStatus('연결 중');
  const pc = new RTCPeerConnection(); // 결정 Q3=a: LAN 전용, ICE 서버 없음
  state.pc = pc;
  watchConnection(pc, log, (s) => {
    if (s === 'connected') setStatus('송출 중', 'live');
    else if (s === 'failed') setStatus('연결 실패', 'error');
    else if (s === 'disconnected') setStatus('연결 끊김(복구 대기)', 'error');
  });

  const stream = new MediaStream();
  const vt = pc.addTransceiver(state.videoTrack, { direction: 'sendonly', streams: [stream] });
  state.videoSender = vt.sender;
  preferVideoCodec(vt, $('codec').value, log);
  // 마이크를 아직 안 켰어도 오디오 자리를 만들어 둔다. 나중에 켜면 replaceTrack으로 채운다.
  const at = pc.addTransceiver(state.audioTrack ?? 'audio', { direction: 'sendonly', streams: [stream] });
  state.audioSender = at.sender;
  if (!state.audioTrack) log('마이크 없이 송출을 시작합니다(나중에 ② 마이크 켜기로 추가 가능).', 'warn');

  try {
    state.sessionUrl = await negotiate(pc, endpoint, log);
    state.stopStats = startStats(pc, 'out', $('stats'));
    $('btnStop').disabled = false;
  } catch (e) {
    log(`WHIP 송출 실패: ${e.message}`, 'error');
    if (e.status === 400) log('같은 이름으로 이미 송출 중인지 확인하세요.', 'warn');
    setStatus('실패', 'error');
    pc.close();
    state.pc = null;
    $('btnStart').disabled = false;
  }
};

$('btnStop').onclick = async () => {
  log('송출 종료 요청');
  $('btnStop').disabled = true;
  state.stopStats?.();
  await deleteSession(state.sessionUrl, log);
  state.sessionUrl = null;
  state.pc?.close();
  state.pc = null;
  state.videoSender = null;
  state.audioSender = null;
  state.videoTrack?.stop();
  state.videoTrack = null;
  state.audioTrack?.stop();
  state.audioTrack = null;
  state.stopMeter?.();
  state.stopMeter = null;
  $('preview').srcObject = null;
  renderTracks();
  setStatus('종료됨');
  $('btnStart').disabled = false;
  log('송출 종료 완료 (카메라·마이크 해제)');
};

window.addEventListener('pagehide', () => {
  if (state.sessionUrl) fetch(state.sessionUrl, { method: 'DELETE', keepalive: true });
});

if (!navigator.mediaDevices?.getUserMedia) {
  log(`getUserMedia를 쓸 수 없습니다.${window.isSecureContext ? '' : ' HTTPS 주소로 접속했는지 확인하세요.'}`, 'error');
  ['btnCam', 'btnMic', 'btnSwitch', 'btnStart'].forEach((id) => ($(id).disabled = true));
}
