import { randomBytes } from 'node:crypto';
import { request } from '@playwright/test';
import { readE2eEnv } from './env';

/** 테스트마다 새로 만드는 계정. 비밀번호·OTP는 일회용 테스트 값이며 로그에 출력하지 않는다. */
export interface TestAccount {
  username: string;
  email: string;
  password: string;
}

function readCsrfToken(setCookies: readonly string[]): string {
  for (const header of setCookies) {
    const match = /^XSRF-TOKEN=([^;]+)/.exec(header);
    if (match?.[1] !== undefined) return decodeURIComponent(match[1]);
  }
  throw new Error('CSRF 토큰 쿠키를 받지 못했습니다');
}

/**
 * Backend 가입 API로 새 계정을 만든다(Frontend dev 서버 프록시 경유, CSRF 이중 제출).
 * 계정마다 username·이메일이 달라 회원별 OTP 제한과 Mailpit 수신함이 섞이지 않는다.
 * 가입은 Backend 설정의 HIBP 주소를 호출한다: 로컬 기본은 실제 HIBP(인터넷 필요), CI는 HIBP 스텁(FE-D044 (4)).
 * 테스트마다 이 함수를 한 번 부르므로, CI는 실행한 테스트 수를 스텁의 최소 정상 호출 수로 쓴다(fe06 Q5).
 */
export async function createAccount(): Promise<TestAccount> {
  const suffix = randomBytes(6).toString('hex');
  const account: TestAccount = {
    username: `e2e_${suffix}`, // 16자 [a-z0-9_]
    email: `e2e_${suffix}@e2e.test`,
    password: randomBytes(18).toString('base64url'), // 24자, 가입 규칙 10~30자 안
  };
  const context = await request.newContext({ baseURL: readE2eEnv().baseUrl });
  try {
    const csrf = await context.get('/api/v1/auth/csrf');
    if (csrf.status() !== 204) throw new Error(`CSRF 발급 실패: ${csrf.status()}`);
    const token = readCsrfToken(
      csrf.headersArray().filter((h) => h.name.toLowerCase() === 'set-cookie').map((h) => h.value),
    );
    // 쿠키는 직접 보낸다(컨텍스트 쿠키 저장소의 Secure 처리에 기대지 않는다)
    await context.clearCookies();
    const signup = await context.post('/api/v1/auth/signup', {
      headers: { 'X-XSRF-TOKEN': token, Cookie: `XSRF-TOKEN=${encodeURIComponent(token)}` },
      data: {
        username: account.username,
        password: account.password,
        passwordConfirm: account.password,
        email: account.email,
        nickname: `E2E${suffix.slice(0, 4)}`,
      },
    });
    if (signup.status() !== 201) throw new Error(`테스트 계정 가입 실패: ${signup.status()}`);
    return account;
  } finally {
    await context.dispose();
  }
}
