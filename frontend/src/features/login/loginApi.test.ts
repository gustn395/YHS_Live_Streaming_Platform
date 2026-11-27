import { http, HttpResponse, type JsonBodyType } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw/server';
import { createApiClient } from '../../shared/api/apiClient';
import { ApiError, type ApiFailure } from '../../shared/api/apiError';
import { submitLogin } from './loginApi';

const LOGIN = '*/api/v1/auth/login';
const EXPIRES = '2026-11-27T10:05:00Z';

async function failureOf(promise: Promise<unknown>): Promise<ApiFailure> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error.failure;
    throw error;
  }
  throw new Error('요청이 성공했다');
}

function captureLogin(response: () => Response) {
  const calls: { body: unknown; token: string | null }[] = [];
  server.use(
    http.post(LOGIN, async ({ request }) => {
      calls.push({ body: await request.json(), token: request.headers.get('x-xsrf-token') });
      return response();
    }),
  );
  return calls;
}

// FE-T030: 로그인 요청·응답 계약(Backend LoginRequest·OtpRequiredResponse, FE-D037)
describe('submitLogin 요청', () => {
  it('username·비밀번호를 입력 그대로 보낸다(형식·길이 검사·소문자 변환·Trim 없음), CSRF 헤더 포함', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const longPassword = ` ${'p'.repeat(62)} `; // 이전 정책(31~64자) 비밀번호도 그대로 보낸다(FE-D033 (3))
    const calls = captureLogin(() => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: EXPIRES }));
    await expect(submitLogin(createApiClient(), { username: 'Tester_01', password: longPassword })).resolves.toEqual({
      otpExpiresAt: Date.parse(EXPIRES),
    });
    expect(calls).toEqual([{ body: { username: 'Tester_01', password: longPassword }, token: 't1' }]);
  });
});

describe('submitLogin 200 계약', () => {
  it.each<[string, JsonBodyType, number]>([
    ['status 누락', { otpExpiresAt: EXPIRES }, 200],
    ['status가 OTP_REQUIRED 아님', { status: 'OK', otpExpiresAt: EXPIRES }, 200],
    ['otpExpiresAt 누락', { status: 'OTP_REQUIRED' }, 200],
    ['otpExpiresAt 날짜 아님', { status: 'OTP_REQUIRED', otpExpiresAt: 'soon' }, 200],
    ['otpExpiresAt 숫자', { status: 'OTP_REQUIRED', otpExpiresAt: 1764237900000 }, 200],
    ['본문 없음', null, 200],
    ['201', { status: 'OTP_REQUIRED', otpExpiresAt: EXPIRES }, 201],
    ['202', { status: 'OTP_REQUIRED', otpExpiresAt: EXPIRES }, 202],
  ])('%s → invalid-response, 재전송 없음', async (_name, body, status) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = captureLogin(() => HttpResponse.json(body, { status }));
    expect(await failureOf(submitLogin(createApiClient(), { username: 'a', password: 'b' }))).toEqual({
      kind: 'invalid-response',
      status,
    });
    expect(calls).toHaveLength(1);
  });

  it('204(본문 없음) → invalid-response', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    captureLogin(() => new HttpResponse(null, { status: 204 }));
    expect(await failureOf(submitLogin(createApiClient(), { username: 'a', password: 'b' }))).toEqual({
      kind: 'invalid-response',
      status: 204,
    });
  });

  it('401은 http 실패로 그대로 돌려주고 재전송하지 않는다', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = captureLogin(() =>
      HttpResponse.json({ code: 'AUTH_LOGIN_FAILED' }, { status: 401, headers: { 'Content-Type': 'application/problem+json' } }),
    );
    const failure = await failureOf(submitLogin(createApiClient(), { username: 'a', password: 'b' }));
    expect(failure).toMatchObject({ kind: 'http', status: 401, problem: { code: 'AUTH_LOGIN_FAILED' } });
    expect(calls).toHaveLength(1);
  });
});
