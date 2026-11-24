// MediaMTX WebRTC POC 공통 모듈
// - WHIP(송출) / WHEP(시청) 협상: SDP offer를 POST하고 201 응답의 answer와 Location(세션 URL)을 받는다.
// - 종료: 세션 URL에 DELETE를 보낸다.
// - 페이지와 WHIP/WHEP 엔드포인트는 같은 Caddy origin(https://<LAN IP>:8443)을 쓴다.

const STREAM_NAME_RE = /^[A-Za-z0-9_-]+$/;

/** 시각이 붙은 로그를 화면과 콘솔에 남긴다. */
export function createLogger(el) {
  return function log(msg, level = 'info') {
    const ts = new Date().toISOString().slice(11, 23);
    const line = `[${ts}] ${msg}`;
    const div = document.createElement('div');
    div.className = `log-${level}`;
    div.textContent = line;
    el.prepend(div);
    (level === 'error' ? console.error : console.log)(line);
  };
}

/** live/<name>/<kind> 엔드포인트 URL을 만든다. kind는 'whip' 또는 'whep'. */
export function endpointFor(streamName, kind) {
  if (!STREAM_NAME_RE.test(streamName)) {
    throw new Error(`스트림 이름은 영문·숫자·_·- 만 쓸 수 있습니다: "${streamName}"`);
  }
  return `${location.origin}/live/${streamName}/${kind}`;
}

/** ICE 후보 수집이 끝날 때까지(또는 timeoutMs까지) 기다린다. (trickle ICE를 쓰지 않는 단순 방식) */
export function waitIceGathering(pc, timeoutMs = 3000) {
  if (pc.iceGatheringState === 'complete') return Promise.resolve();
  return new Promise((resolve) => {
    const timer = setTimeout(done, timeoutMs);
    function done() {
      clearTimeout(timer);
      pc.removeEventListener('icegatheringstatechange', onChange);
      resolve();
    }
    function onChange() {
      if (pc.iceGatheringState === 'complete') done();
    }
    pc.addEventListener('icegatheringstatechange', onChange);
  });
}

/**
 * WHIP/WHEP 협상. offer를 만들어 POST하고 answer를 적용한다.
 * @returns {Promise<string>} 세션 URL (종료 시 DELETE 대상)
 */
export async function negotiate(pc, endpoint, log) {
  const offer = await pc.createOffer();
  await pc.setLocalDescription(offer);
  await waitIceGathering(pc);

  log(`POST ${endpoint} (Content-Type: application/sdp)`);
  const res = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/sdp' },
    body: pc.localDescription.sdp,
  });
  if (res.status !== 201) {
    const body = (await res.text()).trim();
    const err = new Error(`${res.status} ${res.statusText}${body ? ` - ${body}` : ''}`);
    err.status = res.status;
    throw err;
  }
  const location = res.headers.get('Location');
  const sessionUrl = new URL(location, endpoint).toString();
  log(`201 Created, Location: ${location}`);

  const answer = await res.text();
  await pc.setRemoteDescription({ type: 'answer', sdp: answer });
  return sessionUrl;
}

/** 세션 URL에 DELETE를 보내 송출·시청 세션을 끝낸다. */
export async function deleteSession(sessionUrl, log) {
  if (!sessionUrl) return;
  try {
    const res = await fetch(sessionUrl, { method: 'DELETE' });
    log(`DELETE ${new URL(sessionUrl).pathname} → ${res.status}`);
  } catch (e) {
    log(`DELETE 실패: ${e.message}`, 'error');
  }
}

/** 비디오 코덱 우선순위를 정한다. mimeType이 'auto'면 브라우저 기본값을 쓴다. */
export function preferVideoCodec(transceiver, mimeType, log, kind = 'sender') {
  if (mimeType === 'auto') return;
  const Caps = kind === 'sender' ? RTCRtpSender : RTCRtpReceiver;
  if (typeof transceiver.setCodecPreferences !== 'function' || !Caps.getCapabilities) {
    log('이 브라우저는 코덱 우선순위 지정(setCodecPreferences)을 지원하지 않아 기본 코덱을 씁니다.', 'warn');
    return;
  }
  const codecs = Caps.getCapabilities('video').codecs;
  const preferred = codecs.filter((c) => c.mimeType.toLowerCase() === mimeType.toLowerCase());
  if (preferred.length === 0) {
    log(`${mimeType} 코덱을 지원하지 않아 기본 코덱을 씁니다.`, 'warn');
    return;
  }
  const rest = codecs.filter((c) => c.mimeType.toLowerCase() !== mimeType.toLowerCase());
  try {
    transceiver.setCodecPreferences([...preferred, ...rest]);
  } catch (e) {
    log(`코덱 우선순위 지정 실패: ${e.message}`, 'warn');
  }
}

/** RTCPeerConnection 상태 변화를 로그로 남기고 콜백을 부른다. */
export function watchConnection(pc, log, onState) {
  pc.addEventListener('iceconnectionstatechange', () => log(`ICE 상태: ${pc.iceConnectionState}`));
  pc.addEventListener('connectionstatechange', () => {
    log(`연결 상태: ${pc.connectionState}`, pc.connectionState === 'failed' ? 'error' : 'info');
    onState?.(pc.connectionState);
  });
}

/** 선택된 ICE 후보쌍(로컬·원격 주소)을 읽는다. LAN 직접 연결인지 확인하는 데 쓴다. */
async function selectedPair(stats) {
  let pair;
  stats.forEach((s) => {
    if (s.type === 'transport' && s.selectedCandidatePairId) pair = stats.get(s.selectedCandidatePairId);
  });
  if (!pair) {
    stats.forEach((s) => {
      if (s.type === 'candidate-pair' && s.nominated && s.state === 'succeeded') pair = s;
    });
  }
  if (!pair) return null;
  const local = stats.get(pair.localCandidateId);
  const remote = stats.get(pair.remoteCandidateId);
  const addr = (c) => (c ? `${c.address ?? c.ip ?? '?'}:${c.port} (${c.candidateType}/${c.protocol})` : '?');
  return { local: addr(local), remote: addr(remote), rtt: pair.currentRoundTripTime };
}

/**
 * 1초마다 송출(outbound) 또는 수신(inbound) 통계를 표에 그린다.
 * @returns {() => void} 중지 함수
 */
export function startStats(pc, direction, tableEl) {
  const prev = new Map();
  const timer = setInterval(async () => {
    if (pc.connectionState === 'closed') return;
    const stats = await pc.getStats();
    const rows = [];
    const rtpType = direction === 'out' ? 'outbound-rtp' : 'inbound-rtp';
    stats.forEach((s) => {
      if (s.type !== rtpType) return;
      const bytes = direction === 'out' ? s.bytesSent : s.bytesReceived;
      const p = prev.get(s.id);
      const kbps = p ? Math.round(((bytes - p.bytes) * 8) / ((s.timestamp - p.ts) / 1000) / 1000) : 0;
      prev.set(s.id, { bytes, ts: s.timestamp });
      const codec = s.codecId ? stats.get(s.codecId)?.mimeType : '';
      const row = { 종류: s.kind, 코덱: codec ?? '', 'kbps': kbps };
      if (s.kind === 'video') {
        row['해상도'] = s.frameWidth ? `${s.frameWidth}x${s.frameHeight}` : '-';
        row['fps'] = s.framesPerSecond ?? '-';
        if (direction === 'in') {
          row['디코딩 프레임'] = s.framesDecoded ?? '-';
          row['손실 패킷'] = s.packetsLost ?? '-';
        } else {
          row['인코딩 프레임'] = s.framesEncoded ?? '-';
          row['품질 제한'] = s.qualityLimitationReason ?? '-';
        }
      } else {
        if (direction === 'in') {
          row['음량(0~1)'] = s.audioLevel != null ? s.audioLevel.toFixed(3) : '-';
          row['손실 패킷'] = s.packetsLost ?? '-';
          row['지터(ms)'] = s.jitter != null ? Math.round(s.jitter * 1000) : '-';
        } else {
          row['전송 패킷'] = s.packetsSent ?? '-';
        }
      }
      rows.push(row);
    });
    const pair = await selectedPair(stats);
    renderTable(tableEl, rows, pair);
  }, 1000);
  return () => clearInterval(timer);
}

function renderTable(el, rows, pair) {
  el.textContent = '';
  if (pair) {
    const p = document.createElement('p');
    p.className = 'pair';
    const rtt = pair.rtt != null ? `, RTT ${Math.round(pair.rtt * 1000)}ms` : '';
    p.textContent = `ICE 후보쌍: 로컬 ${pair.local} ↔ 원격 ${pair.remote}${rtt}`;
    el.append(p);
  }
  if (rows.length === 0) return;
  const keys = [...new Set(rows.flatMap((r) => Object.keys(r)))];
  const table = document.createElement('table');
  const head = table.createTHead().insertRow();
  keys.forEach((k) => {
    const th = document.createElement('th');
    th.textContent = k;
    head.append(th);
  });
  const body = table.createTBody();
  rows.forEach((r) => {
    const tr = body.insertRow();
    keys.forEach((k) => (tr.insertCell().textContent = r[k] ?? ''));
  });
  el.append(table);
}

/** 브라우저 지원 여부를 표로 보여 준다. 결과 문서의 브라우저별 지원 여부 기록에 쓴다. */
export function renderEnvironment(el) {
  const md = navigator.mediaDevices;
  const items = [
    ['보안 컨텍스트(isSecureContext)', window.isSecureContext],
    ['RTCPeerConnection', typeof RTCPeerConnection === 'function'],
    ['getUserMedia', !!md?.getUserMedia],
    ['getDisplayMedia', !!md?.getDisplayMedia],
    ['AudioContext', typeof (window.AudioContext ?? window.webkitAudioContext) === 'function'],
    ['setCodecPreferences', typeof RTCRtpTransceiver !== 'undefined' && 'setCodecPreferences' in RTCRtpTransceiver.prototype],
  ];
  el.textContent = '';
  const ua = document.createElement('p');
  ua.className = 'ua';
  ua.textContent = navigator.userAgent;
  el.append(ua);
  const ul = document.createElement('ul');
  ul.className = 'env';
  items.forEach(([name, ok]) => {
    const li = document.createElement('li');
    li.className = ok ? 'ok' : 'ng';
    li.textContent = `${ok ? '✔' : '✘'} ${name}`;
    ul.append(li);
  });
  el.append(ul);
}

/** 오디오 트랙의 음량을 막대로 보여 준다. 마이크·믹싱 결과가 실제로 들어오는지 확인용. */
export function startLevelMeter(audioCtx, sourceNode, meterEl) {
  const analyser = audioCtx.createAnalyser();
  analyser.fftSize = 512;
  sourceNode.connect(analyser);
  const data = new Uint8Array(analyser.fftSize);
  let raf = 0;
  const draw = () => {
    analyser.getByteTimeDomainData(data);
    let peak = 0;
    for (const v of data) peak = Math.max(peak, Math.abs(v - 128));
    meterEl.value = peak / 128;
    raf = requestAnimationFrame(draw);
  };
  draw();
  return () => {
    cancelAnimationFrame(raf);
    meterEl.value = 0;
    try { sourceNode.disconnect(analyser); } catch { /* 이미 끊김 */ }
  };
}

/** 트랙 정보를 사람이 읽기 쉬운 문자열로 만든다. */
export function describeTrack(track) {
  const s = track.getSettings?.() ?? {};
  const parts = [`${track.kind}: ${track.label || '(이름 없음)'}`];
  if (track.kind === 'video') {
    if (s.width) parts.push(`${s.width}x${s.height}`);
    if (s.frameRate) parts.push(`${Math.round(s.frameRate)}fps`);
    if (s.facingMode) parts.push(`facingMode=${s.facingMode}`);
    if (s.displaySurface) parts.push(`displaySurface=${s.displaySurface}`);
  } else {
    if (s.sampleRate) parts.push(`${s.sampleRate}Hz`);
    if (s.channelCount) parts.push(`${s.channelCount}ch`);
  }
  return parts.join(', ');
}
