// @vitest-environment node
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { createServer } from 'node:http';
import { connect } from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { BACKEND_USER_AGENT, startHibpStub, type RunningStub } from './hibpStub';

/**
 * HIBP 스텁 계약(FE-T040, fe06-e2e-ci.md Q5). 기준은 Backend HibpPwnedPasswordsClient:
 * GET {base}/range/{대문자 16진수 5자}, Add-Padding: true, User-Agent: yhs-live-streaming-backend,
 * 200 응답의 `[0-9A-Fa-f]{35}:\d+` 줄이 1개 이상이어야 판정한다(0줄은 UNAVAILABLE).
 */
const BACKEND_ENTRY = /^([0-9A-Fa-f]{35}):([0-9]+)$/;
const GOOD_HEADERS = { 'Add-Padding': 'true', 'User-Agent': BACKEND_USER_AGENT };

let dir: string;
let statsFile: string;
let stub: RunningStub;

beforeEach(async () => {
  dir = mkdtempSync(path.join(os.tmpdir(), 'hibp-stub-test-'));
  statsFile = path.join(dir, 'stats.json');
  stub = await startHibpStub({ port: 0, statsFile });
});

afterEach(async () => {
  await stub.close();
  rmSync(dir, { recursive: true, force: true });
});

interface RawResponse {
  status: number;
  headers: Record<string, string>;
  text: () => Promise<string>;
}

/**
 * 공통 setup의 MSW가 fetch·http 클라이언트를 가로채므로, 테스트는 TCP 소켓으로 HTTP/1.1 요청을 직접 보낸다.
 * 스텁은 응답마다 연결을 닫으므로 응답 끝까지 읽는다.
 */
function fetch(
  target: string,
  init: { method?: string; headers?: Record<string, string>; body?: string } = {},
): Promise<RawResponse> {
  const { method = 'GET', headers = {}, body } = init;
  const lines = [`${method} ${target} HTTP/1.1`, `Host: 127.0.0.1:${stub.port}`, 'Connection: close'];
  for (const [name, value] of Object.entries(headers)) lines.push(`${name}: ${value}`);
  if (body !== undefined) lines.push(`Content-Length: ${Buffer.byteLength(body)}`);
  return new Promise((resolve, reject) => {
    const socket = connect(stub.port, '127.0.0.1');
    const chunks: Buffer[] = [];
    socket.on('data', (chunk: Buffer) => chunks.push(chunk));
    socket.on('error', reject);
    socket.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      const split = raw.indexOf('\r\n\r\n');
      const [statusLine = '', ...headerLines] = raw.slice(0, split).split('\r\n');
      const parsed: Record<string, string> = {};
      for (const line of headerLines) {
        const i = line.indexOf(':');
        parsed[line.slice(0, i).trim().toLowerCase()] = line.slice(i + 1).trim();
      }
      const payload = raw.slice(split + 4);
      resolve({ status: Number(statusLine.split(' ')[1]), headers: parsed, text: () => Promise.resolve(payload) });
    });
    socket.write(`${lines.join('\r\n')}\r\n\r\n${body ?? ''}`);
  });
}

function url(p: string): string {
  return p;
}

function readStats(): { ok: number; unexpected: number; health: number } {
  return JSON.parse(readFileSync(statsFile, 'utf8')) as { ok: number; unexpected: number; health: number };
}

describe('HIBP 스텁', () => {
  it('Backend와 같은 요청에 패딩만 있는 200 응답을 주고, Backend 정규식으로 "유출 없음"이 된다', async () => {
    const res = await fetch(url('/range/5BAA6'), { headers: GOOD_HEADERS });
    expect(res.status).toBe(200);
    expect(res.headers['content-type']).toMatch(/^text\/plain/);
    const lines = (await res.text()).split(/\r?\n/).filter((l) => l.trim() !== '');
    expect(lines.length).toBeGreaterThan(0);
    for (const line of lines) {
      const m = BACKEND_ENTRY.exec(line);
      expect(m).not.toBeNull();
      expect(Number(m?.[2])).toBe(0); // 건수 0은 패딩이라 Backend가 유출로 보지 않는다
    }
    expect(readStats()).toEqual({ ok: 1, unexpected: 0, health: 0 });
  });

  it('통계 파일은 기동하면 0으로 초기화되고, 서버가 떠 있는 동안에도 최신 값이다', async () => {
    expect(readStats()).toEqual({ ok: 0, unexpected: 0, health: 0 });
    await fetch(url('/range/00000'), { headers: GOOD_HEADERS });
    await fetch(url('/range/FFFFF'), { headers: GOOD_HEADERS });
    expect(readStats().ok).toBe(2);
  });

  it('이전 실행의 통계 파일을 덮어써 초기화한다', async () => {
    await fetch(url('/range/00000'), { headers: GOOD_HEADERS });
    await stub.close();
    stub = await startHibpStub({ port: 0, statsFile });
    expect(readStats()).toEqual({ ok: 0, unexpected: 0, health: 0 });
  });

  it('Health Check는 200이지만 정상 호출로 세지 않는다', async () => {
    const res = await fetch(url('/healthz'));
    expect(res.status).toBe(200);
    expect(readStats()).toEqual({ ok: 0, unexpected: 0, health: 1 });
  });

  it.each([
    ['소문자 prefix', '/range/5baa6', GOOD_HEADERS, 400],
    ['4자 prefix', '/range/5BAA', GOOD_HEADERS, 400],
    ['6자 prefix', '/range/5BAA61', GOOD_HEADERS, 400],
    ['16진수 아님', '/range/5BAG6', GOOD_HEADERS, 400],
    ['쿼리 붙음', '/range/5BAA6?x=1', GOOD_HEADERS, 400],
    ['Add-Padding 없음', '/range/5BAA6', { 'User-Agent': BACKEND_USER_AGENT }, 400],
    ['Add-Padding false', '/range/5BAA6', { ...GOOD_HEADERS, 'Add-Padding': 'false' }, 400],
    ['User-Agent 다름', '/range/5BAA6', { ...GOOD_HEADERS, 'User-Agent': 'curl/8' }, 400],
    ['다른 경로', '/api/range/5BAA6', GOOD_HEADERS, 404],
    ['루트', '/', GOOD_HEADERS, 404],
  ])('예상 밖 요청(%s)은 %s 응답이 아닌 실패이고 unexpected로 센다', async (_name, p, headers, status) => {
    const res = await fetch(url(p), { headers });
    expect(res.status).toBe(status);
    await res.text();
    expect(readStats()).toEqual({ ok: 0, unexpected: 1, health: 0 });
  });

  it('GET이 아닌 메서드는 400이고 unexpected로 센다', async () => {
    const res = await fetch(url('/range/5BAA6'), { method: 'POST', headers: GOOD_HEADERS, body: 'x' });
    expect(res.status).toBe(400);
    expect(readStats().unexpected).toBe(1);
  });

  it('loopback(127.0.0.1)에만 바인드한다', () => {
    expect(stub.host).toBe('127.0.0.1');
  });

  it('포트가 이미 쓰이고 있으면 다른 포트로 바꾸지 않고 실패한다', async () => {
    const blocker = createServer();
    await new Promise<void>((resolve) => blocker.listen(0, '127.0.0.1', resolve));
    const address = blocker.address();
    const busy = typeof address === 'object' && address !== null ? address.port : -1;
    try {
      await expect(startHibpStub({ port: busy, statsFile: path.join(dir, 'other.json') })).rejects.toThrow();
    } finally {
      await new Promise<void>((resolve) => blocker.close(() => resolve()));
    }
  });
});
