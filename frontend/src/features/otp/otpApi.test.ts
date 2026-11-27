import { http, HttpResponse, type JsonBodyType } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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

// FE-T031(CodeRabbit 반영): 시각은 Backend Instant 계약(ISO-8601 UTC `Z`)만 받는다. Date.parse가 받아들이는 다른 형식은 거부
describe('OTP 응답 시각 형식', () => {
  it.each([
    ['RFC 1123', 'Fri, 27 Nov 2026 10:05:00 GMT'],
    ['시간대 없음', '2026-11-27T10:05:00'],
    ['날짜만', '2026-11-27'],
    ['오프셋 표기', '2026-11-27T19:05:00+09:00'],
    ['소수 초 10자리', '2026-11-27T10:05:00.1234567890Z'],
  ])('%s(%s) → invalid-response', async (_name, value) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    capture(RESEND, () => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: value }));
    expect(await failureOf(resendOtp(createApiClient()))).toEqual({ kind: 'invalid-response', status: 200 });
  });

  // 정규식은 모양만 본다. Date.parse는 없는 날짜·시각을 다음 날로 넘기므로 해석 결과가 입력과 같은지 확인한다(CodeRabbit 2차)
  it.each([
    ['2월 30일', '2026-02-30T00:00:00Z'],
    ['4월 31일', '2026-04-31T10:00:00Z'],
    ['평년 2월 29일', '2023-02-29T00:00:00Z'],
    ['24시', '2026-11-27T24:00:00Z'],
  ])('없는 날짜·시각 %s(%s) → invalid-response', async (_name, value) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    capture(RESEND, () => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: value }));
    expect(await failureOf(resendOtp(createApiClient()))).toEqual({ kind: 'invalid-response', status: 200 });
  });

  it.each([
    '2026-11-27T10:05:00Z',
    '2026-11-27T10:05:00.123Z',
    '2026-11-27T10:05:00.123456789Z',
    '2024-02-29T23:59:59.999999999Z',
  ])('%s는 허용', async (value) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    capture(RESEND, () => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: value }));
    await expect(resendOtp(createApiClient())).resolves.toEqual({ otpExpiresAt: Date.parse(value) });
  });

  it.each([
    ['0월', '2026-00-10T00:00:00Z'],
    ['13월', '2026-13-01T00:00:00Z'],
    ['0일', '2026-11-00T00:00:00Z'],
    ['32일', '2026-01-32T00:00:00Z'],
    ['60분', '2026-11-27T23:60:00Z'],
    ['60초', '2026-11-27T23:59:60Z'],
    ['1900년(평년) 2월 29일', '1900-02-29T00:00:00Z'],
  ])('범위를 넘는 값 %s(%s) → invalid-response', async (_name, value) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    capture(RESEND, () => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: value }));
    expect(await failureOf(resendOtp(createApiClient()))).toEqual({ kind: 'invalid-response', status: 200 });
  });

  // 엔진마다 다른 Date.parse(밀리초를 넘는 소수 초 처리)에 기대지 않고, 0~99년도 1900년대로 바꾸지 않는다(CodeRabbit 2차, Q1=a)
  describe('엔진의 Date.parse에 기대지 않는 해석', () => {
    beforeEach(() => {
      vi.spyOn(Date, 'parse').mockReturnValue(Number.NaN);
    });

    it.each<[string, number]>([
      ['2026-11-27T10:05:00Z', Date.UTC(2026, 10, 27, 10, 5, 0)],
      ['2026-11-27T10:05:59.9999999Z', 1795773959999], // 소수 초는 반올림하지 않고 밀리초로 잘라낸다
      ['2024-02-29T23:59:59.999999999Z', 1709251199999],
      ['0000-01-01T00:00:00Z', -62167219200000],
      ['0050-06-15T12:30:45.123456Z', -60574994954877], // Date.UTC(50, …)는 1950년이 된다
      ['0099-12-31T23:59:59.999999999Z', -59011459200001],
    ])('%s → %d', async (value, expected) => {
      document.cookie = 'XSRF-TOKEN=t1; path=/';
      capture(RESEND, () => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: value }));
      await expect(resendOtp(createApiClient())).resolves.toEqual({ otpExpiresAt: expected });
    });

    it('없는 날짜는 Date.parse 없이도 거부한다', async () => {
      document.cookie = 'XSRF-TOKEN=t1; path=/';
      capture(RESEND, () => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: '2026-02-30T00:00:00Z' }));
      expect(await failureOf(resendOtp(createApiClient()))).toEqual({ kind: 'invalid-response', status: 200 });
    });
  });

  it('로그인 성공 응답의 accessTokenExpiresAt도 같은 규칙(RFC 1123 거부)', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    capture(VERIFY, () => HttpResponse.json({ ...SUCCESS, accessTokenExpiresAt: 'Fri, 27 Nov 2026 10:15:00 GMT' }));
    expect(await failureOf(verifyOtp(createApiClient(), '1234'))).toEqual({ kind: 'invalid-response', status: 200 });
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
