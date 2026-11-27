import { renameSync, writeFileSync } from 'node:fs';
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';
import { pathToFileURL } from 'node:url';

/**
 * CI E2E용 HIBP Pwned Passwords range API 스텁(FE-D044 (4), fe06-e2e-ci.md Q5, 계약 FE-T040).
 * Backend `MEMBER_PASSWORD_HIBP_BASE_URL`을 이 서버로 지정한다. 실제 HIBP로 대신 요청하는 코드는 없다.
 *
 * - 계약 기준은 Backend HibpPwnedPasswordsClient: `GET /range/{대문자 16진수 5자}`, `Add-Padding: true`,
 *   `User-Agent: yhs-live-streaming-backend`. 응답은 건수 0인 패딩 줄만이라 Backend는 "유출 없음"으로 판정한다.
 * - 그 밖의 요청은 400(경로가 다르면 404)이고 unexpected로 센다. Backend는 이를 503으로 처리하므로 E2E도 실패한다.
 * - 통계 파일은 기동 때 0으로 초기화하고 요청마다 원자적으로(임시 파일 → rename) 다시 쓴다. Health Check는 따로 센다.
 * - 로그에는 prefix·헤더 값·요청 원문을 남기지 않는다. 127.0.0.1에만 바인드하고, 포트가 쓰이고 있으면 실패한다.
 *
 * 실행: `HIBP_STUB_PORT=18099 HIBP_STUB_STATS_FILE=<경로> node frontend/e2e/support/hibpStub.ts`
 */
export const BACKEND_USER_AGENT = 'yhs-live-streaming-backend';
const HOST = '127.0.0.1';
const RANGE_PATH = /^\/range\/([0-9A-F]{5})$/;
const PADDING_BODY = ['0'.repeat(35), '1'.repeat(35), 'F'.repeat(35)].map((suffix) => `${suffix}:0`).join('\r\n');

export interface StubStats {
  ok: number;
  unexpected: number;
  health: number;
}

export interface RunningStub {
  host: string;
  port: number;
  close: () => Promise<void>;
}

type Outcome = { kind: keyof StubStats; status: number; body: string };

function classifyRequest(req: IncomingMessage): Outcome {
  const target = req.url ?? '';
  if (req.method === 'GET' && target === '/healthz') return { kind: 'health', status: 200, body: 'ok' };
  if (!target.startsWith('/range/')) return { kind: 'unexpected', status: 404, body: 'not found' };
  const valid =
    req.method === 'GET' &&
    RANGE_PATH.test(target) &&
    req.headers['add-padding'] === 'true' &&
    req.headers['user-agent'] === BACKEND_USER_AGENT;
  return valid ? { kind: 'ok', status: 200, body: PADDING_BODY } : { kind: 'unexpected', status: 400, body: 'bad request' };
}

function writeStats(file: string, stats: StubStats): void {
  const tmp = `${file}.tmp`;
  writeFileSync(tmp, JSON.stringify(stats), 'utf8');
  renameSync(tmp, file);
}

export async function startHibpStub(options: { port: number; statsFile: string }): Promise<RunningStub> {
  const stats: StubStats = { ok: 0, unexpected: 0, health: 0 };
  writeStats(options.statsFile, stats);
  const server: Server = createServer((req: IncomingMessage, res: ServerResponse) => {
    req.resume(); // 본문은 읽지 않고 버린다
    const outcome = classifyRequest(req);
    stats[outcome.kind] += 1;
    writeStats(options.statsFile, stats);
    res.writeHead(outcome.status, {
      'Content-Type': 'text/plain; charset=utf-8',
      'Content-Length': Buffer.byteLength(outcome.body),
      Connection: 'close',
    });
    res.end(outcome.body);
  });
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject);
    server.listen(options.port, HOST, () => {
      server.off('error', reject);
      resolve();
    });
  });
  const address = server.address();
  if (address === null || typeof address === 'string') throw new Error('HIBP 스텁 주소를 확인할 수 없습니다');
  return {
    host: address.address,
    port: address.port,
    close: () =>
      new Promise<void>((resolve) => {
        server.closeAllConnections();
        server.close(() => resolve());
      }),
  };
}

function requiredEnv(name: string): string {
  const value = process.env[name];
  if (value === undefined || value.trim() === '') throw new Error(`환경변수 ${name}가 없습니다`);
  return value;
}

async function main(): Promise<void> {
  const port = Number(requiredEnv('HIBP_STUB_PORT'));
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('HIBP_STUB_PORT가 올바르지 않습니다');
  const stub = await startHibpStub({ port, statsFile: requiredEnv('HIBP_STUB_STATS_FILE') });
  console.log(`hibp-stub: listening ${stub.host}:${stub.port}`);
  const stop = (): void => {
    void stub.close().then(() => process.exit(0));
  };
  process.once('SIGTERM', stop);
  process.once('SIGINT', stop);
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error: unknown) => {
    console.error(`hibp-stub: 기동 실패 — ${error instanceof Error ? error.message : '알 수 없는 오류'}`);
    process.exit(1);
  });
}
