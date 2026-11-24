// PC 송출: getDisplayMedia(화면) + getUserMedia(마이크) → Web Audio 믹싱 → WHIP
import {
  createLogger, endpointFor, negotiate, deleteSession, preferVideoCodec,
  watchConnection, startStats, renderEnvironment, startLevelMeter, describeTrack,
} from './common.js';

const $ = (id) => document.getElementById(id);
const log = createLogger($('log'));
renderEnvironment($('env'));

const state = {
  displayStream: null, // 화면 공유 스트림 (video + 선택적으로 audio)
  micStream: null,
  audioCtx: null,
  mixBus: null, // 모든 오디오 소스가 모이는 노드
  mixDest: null, // 믹싱 결과를 MediaStreamTrack으로 내보내는 노드
  displayGain: null,
  micGain: null,
  displaySource: null,
  micSource: null,
  stopMeter: null,
  pc: null,
  sessionUrl: null,
  stopStats: null,
};

function setStatus(text, cls = '') {
  $('status').textContent = text;
  $('status').className = `status ${cls}`;
}

// AudioContext는 사용자 클릭 안에서 만들어야 자동 재생 정책에 걸리지 않는다.
function ensureMixer() {
  if (state.audioCtx) return;
  const Ctx = window.AudioContext ?? window.webkitAudioContext;
  const ctx = new Ctx();
  state.audioCtx = ctx;
  state.mixBus = ctx.createGain();
  state.mixDest = ctx.createMediaStreamDestination();
  state.displayGain = ctx.createGain();
  state.micGain = ctx.createGain();
  state.displayGain.connect(state.mixBus);
  state.micGain.connect(state.mixBus);
  state.mixBus.connect(state.mixDest);
  state.displayGain.gain.value = Number($('gainDisplay').value);
  state.micGain.gain.value = Number($('gainMic').value);
  state.stopMeter = startLevelMeter(ctx, state.mixBus, $('meter'));
  log(`오디오 믹서 생성 (sampleRate ${ctx.sampleRate}Hz)`);
}

function renderTracks() {
  const ul = $('tracks');
  ul.textContent = '';
  const add = (text) => {
    const li = document.createElement('li');
    li.textContent = text;
    ul.append(li);
  };
  state.displayStream?.getTracks().forEach((t) => add(`화면 ${describeTrack(t)}`));
  state.micStream?.getTracks().forEach((t) => add(`마이크 ${describeTrack(t)}`));
  if (state.displayStream && state.displayStream.getAudioTracks().length === 0) {
    add('화면 오디오 없음 (선택한 화면 종류나 브라우저가 지원하지 않거나, 공유 창에서 오디오 공유를 끔)');
  }
}

$('btnDisplay').onclick = async () => {
  ensureMixer();
  try {
    state.displayStream?.getTracks().forEach((t) => t.stop());
    const wantAudio = $('wantDisplayAudio').checked;
    const stream = await navigator.mediaDevices.getDisplayMedia({
      video: { frameRate: { ideal: 30, max: 30 } },
      audio: wantAudio,
      // Chrome 전용 힌트. 다른 브라우저는 무시한다.
      systemAudio: wantAudio ? 'include' : 'exclude',
      selfBrowserSurface: 'exclude',
      surfaceSwitching: 'include',
    });
    state.displayStream = stream;
    const [video] = stream.getVideoTracks();
    video.contentHint = $('hint').value;
    video.addEventListener('ended', () => {
      log('브라우저의 "공유 중지"로 화면 공유가 끝났습니다.', 'warn');
      if (state.pc) stopPublishing();
    });
    $('preview').srcObject = new MediaStream([video]);

    state.displaySource?.disconnect();
    state.displaySource = null;
    const [audio] = stream.getAudioTracks();
    if (audio) {
      state.displaySource = state.audioCtx.createMediaStreamSource(new MediaStream([audio]));
      state.displaySource.connect(state.displayGain);
    }
    // 송출 중에 화면을 다시 고르면 트랙만 바꾼다(재협상 없음).
    const videoSender = state.pc?.getSenders().find((s) => s.track?.kind === 'video');
    if (videoSender) await videoSender.replaceTrack(video);
    log(`화면 선택됨: ${describeTrack(video)}${audio ? ' + 화면 오디오' : ', 화면 오디오 없음'}`);
    renderTracks();
  } catch (e) {
    log(`화면 선택 실패: ${e.name} ${e.message}`, 'error');
  }
};

$('btnMic').onclick = async () => {
  ensureMixer();
  try {
    state.micStream?.getTracks().forEach((t) => t.stop());
    const deviceId = $('micSelect').value;
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        deviceId: deviceId ? { exact: deviceId } : undefined,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
      video: false,
    });
    state.micStream = stream;
    state.micSource?.disconnect();
    state.micSource = state.audioCtx.createMediaStreamSource(stream);
    state.micSource.connect(state.micGain);
    log(`마이크 켜짐: ${describeTrack(stream.getAudioTracks()[0])}`);
    await refreshMicList();
    renderTracks();
  } catch (e) {
    log(`마이크 실패: ${e.name} ${e.message}`, 'error');
  }
};

// 권한을 받은 뒤에야 장치 이름이 보인다.
async function refreshMicList() {
  const devices = await navigator.mediaDevices.enumerateDevices();
  const sel = $('micSelect');
  const current = state.micStream?.getAudioTracks()[0]?.getSettings().deviceId ?? sel.value;
  sel.textContent = '';
  sel.append(new Option('기본 장치', ''));
  devices.filter((d) => d.kind === 'audioinput').forEach((d) => {
    sel.append(new Option(d.label || d.deviceId.slice(0, 8), d.deviceId, false, d.deviceId === current));
  });
}

$('gainDisplay').oninput = (e) => { if (state.displayGain) state.displayGain.gain.value = Number(e.target.value); };
$('gainMic').oninput = (e) => { if (state.micGain) state.micGain.gain.value = Number(e.target.value); };
$('hint').onchange = (e) => {
  const v = state.displayStream?.getVideoTracks()[0];
  if (v) v.contentHint = e.target.value;
};
$('stream').oninput = (e) => {
  const href = `play.html?stream=${encodeURIComponent(e.target.value)}`;
  $('playLink').href = href;
  $('playLink').textContent = href;
};

$('btnStart').onclick = async () => {
  const video = state.displayStream?.getVideoTracks()[0];
  if (!video || video.readyState !== 'live') {
    log('먼저 ① 화면 선택을 하세요.', 'warn');
    return;
  }
  let endpoint;
  try {
    endpoint = endpointFor($('stream').value.trim(), 'whip');
  } catch (e) {
    log(e.message, 'error');
    return;
  }
  ensureMixer();
  await state.audioCtx.resume();

  $('btnStart').disabled = true;
  setStatus('연결 중');
  const pc = new RTCPeerConnection(); // 결정 Q3=a: LAN 전용이라 ICE 서버(STUN/TURN) 없음
  state.pc = pc;
  watchConnection(pc, log, (s) => {
    if (s === 'connected') setStatus('송출 중', 'live');
    else if (s === 'failed') setStatus('연결 실패', 'error');
    else if (s === 'disconnected') setStatus('연결 끊김(복구 대기)', 'error');
  });

  const stream = new MediaStream();
  const vt = pc.addTransceiver(video, { direction: 'sendonly', streams: [stream] });
  preferVideoCodec(vt, $('codec').value, log);

  // 믹싱 결과 트랙은 소스가 없어도 항상 보낸다. 송출 중 마이크를 켜도 재협상이 필요 없다.
  const mixed = state.mixDest.stream.getAudioTracks()[0];
  pc.addTransceiver(mixed, { direction: 'sendonly', streams: [stream] });
  const sources = [state.displaySource && '화면 오디오', state.micSource && '마이크'].filter(Boolean);
  log(`오디오 소스: ${sources.length ? sources.join(' + ') : '없음(무음 트랙 송출)'}`);

  try {
    state.sessionUrl = await negotiate(pc, endpoint, log);
    const sender = vt.sender;
    const params = sender.getParameters();
    if (!params.encodings || params.encodings.length === 0) params.encodings = [{}];
    params.encodings[0].maxBitrate = Number($('bitrate').value);
    await sender.setParameters(params).catch((e) => log(`비트레이트 설정 실패: ${e.message}`, 'warn'));
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

async function stopPublishing() {
  log('송출 중단 요청');
  $('btnStop').disabled = true;
  state.stopStats?.();
  state.stopStats = null;
  await deleteSession(state.sessionUrl, log);
  state.sessionUrl = null;
  state.pc?.close();
  state.pc = null;
  state.displayStream?.getTracks().forEach((t) => t.stop());
  state.displayStream = null;
  state.micStream?.getTracks().forEach((t) => t.stop());
  state.micStream = null;
  state.displaySource?.disconnect();
  state.displaySource = null;
  state.micSource?.disconnect();
  state.micSource = null;
  $('preview').srcObject = null;
  renderTracks();
  setStatus('중단됨');
  $('btnStart').disabled = false;
  log('송출 중단 완료 (화면·마이크 캡처도 종료)');
}

$('btnStop').onclick = stopPublishing;

// 페이지를 닫을 때도 세션을 정리한다.
window.addEventListener('pagehide', () => {
  if (state.sessionUrl) fetch(state.sessionUrl, { method: 'DELETE', keepalive: true });
});

if (!navigator.mediaDevices?.getDisplayMedia) {
  log('이 브라우저는 getDisplayMedia(화면 공유)를 지원하지 않습니다.', 'error');
  $('btnDisplay').disabled = true;
}
