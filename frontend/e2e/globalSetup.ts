import { readE2eEnv } from './support/env';

/** 점검 요청 하나의 시간 제한. 연결만 받고 응답하지 않는 서버에서 멈추지 않고 아래 안내로 끝낸다. */
const CHECK_TIMEOUT_MS = 5_000;

async function check(name: string, url: string, ok: (status: number) => boolean): Promise<void> {
  let status: number;
  try {
    status = (await fetch(url, { redirect: 'manual', signal: AbortSignal.timeout(CHECK_TIMEOUT_MS) })).status;
  } catch (error) {
    throw new Error(`E2E 환경 미기동: ${name}에 연결할 수 없습니다(${url}). test-spec의 기동 절차를 확인하세요.`, { cause: error });
  }
  if (!ok(status)) throw new Error(`E2E 환경 미기동: ${name} 응답 ${status}(${url})`);
}

/** 테스트 전에 Frontend·Backend(프록시 경유)·Mailpit이 떠 있는지 확인하고, 아니면 분명한 메시지로 멈춘다(FE-D039). */
export default async function globalSetup(): Promise<void> {
  const env = readE2eEnv();
  await check('Frontend 서버(로컬 dev 또는 CI preview)', `${env.baseUrl}/`, (s) => s === 200);
  await check('Backend(프록시 경유 CSRF 발급)', `${env.baseUrl}/api/v1/auth/csrf`, (s) => s === 204);
  await check('Mailpit API', `${env.mailpitUrl}/api/v1/info`, (s) => s === 200);
}
