import { http, HttpResponse, type JsonBodyType } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw/server';
import { createApiClient } from '../../shared/api/apiClient';
import { ApiError, type ApiFailure } from '../../shared/api/apiError';
import { resendOtp, verifyOtp } from './otpApi';

const VERIFY = '*/api/v1/auth/otp/verify';
const RESEND = '*/api/v1/auth/otp/resend';
const AT_EXPIRES = '2026-11-27T10:15:00Z';
const OTP_EXPIRES = '2026-11-27T10:05:00Z';
const SUCCESS = { memberId: 7, role: 'USER', accessTokenExpiresAt: AT_EXPIRES };

async function failureOf(promise: Promise<unknown>): Promise<ApiFailure> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error.failure;
    throw error;
  }
  throw new Error('요청이 성공했다');
}

function capture(path: string, response: () => Response) {
  const calls: { body: string; token: string | null }[] = [];
  server.use(
    http.post(path, async ({ request }) => {
      calls.push({ body: await request.text(), token: request.headers.get('x-xsrf-token') });
      return response();
    }),
  );
  return calls;
}

// FE-T031: OTP 확인·재발송 계약(Backend OtpVerifyRequest·LoginSuccessResponse·OtpRequiredResponse)
describe('verifyOtp', () => {
  it('code를 문자열로 보내 앞자리 0을 지키고, 성공 본문을 principal로 돌려준다', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = capture(VERIFY, () => HttpResponse.json(SUCCESS));
    await expect(verifyOtp(createApiClient(), '0420')).resolves.toEqual({
      memberId: 7,
      role: 'USER',
      accessTokenExpiresAt: Date.parse(AT_EXPIRES),
    });
    expect(calls).toEqual([{ body: '{"code":"0420"}', token: 't1' }]);
  });

  it.each<[string, JsonBodyType, number]>([
    ['memberId 0', { ...SUCCESS, memberId: 0 }, 200],
    ['memberId 소수', { ...SUCCESS, memberId: 1.5 }, 200],
    ['memberId 문자열', { ...SUCCESS, memberId: '7' }, 200],
    ['memberId 안전 정수 초과', { ...SUCCESS, memberId: 2 ** 53 }, 200],
    ['role 빈 문자열', { ...SUCCESS, role: '' }, 200],
    ['role 누락', { memberId: 7, accessTokenExpiresAt: AT_EXPIRES }, 200],
    ['accessTokenExpiresAt 날짜 아님', { ...SUCCESS, accessTokenExpiresAt: 'x' }, 200],
    ['본문 없음', null, 200],
    ['201', SUCCESS, 201],
  ])('%s → invalid-response, 재전송 없음', async (_name, body, status) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = capture(VERIFY, () => HttpResponse.json(body, { status }));
    expect(await failureOf(verifyOtp(createApiClient(), '1234'))).toEqual({ kind: 'invalid-response', status });
    expect(calls).toHaveLength(1);
  });
});

describe('resendOtp', () => {
  it('본문 없이 보내고 새 OTP 만료 시각을 돌려준다', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = capture(RESEND, () => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: OTP_EXPIRES }));
    await expect(resendOtp(createApiClient())).resolves.toEqual({ otpExpiresAt: Date.parse(OTP_EXPIRES) });
    expect(calls).toEqual([{ body: '', token: 't1' }]);
  });

  it.each<[string, JsonBodyType]>([
    ['status 다름', { status: 'X', otpExpiresAt: OTP_EXPIRES }],
    ['만료 시각 없음', { status: 'OTP_REQUIRED' }],
  ])('%s → invalid-response', async (_name, body) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    capture(RESEND, () => HttpResponse.json(body));
    expect(await failureOf(resendOtp(createApiClient()))).toEqual({ kind: 'invalid-response', status: 200 });
  });
});
