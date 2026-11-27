import { describe, expect, it } from 'vitest';
import type { ApiFailure } from './apiError';
import { classifyFormFailure } from './formFailure';

const http = (status: number, code?: string, retryAfterSeconds?: number): ApiFailure => ({
  kind: 'http',
  status,
  problem: code === undefined ? null : { code, fieldErrors: [] },
  ...(retryAfterSeconds === undefined ? {} : { retryAfterSeconds }),
});

// FE-T033·FE-T036 공통: 로그인·OTP 폼의 실패 분류(FE-D037, FE-D040 (2))
describe('classifyFormFailure', () => {
  it.each<[string, ApiFailure, ReturnType<typeof classifyFormFailure>]>([
    ['취소', { kind: 'canceled' }, { kind: 'silent' }],
    ['CSRF 403', http(403, 'AUTH_CSRF_INVALID'), { kind: 'csrf-retry' }],
    ['CSRF 쿠키 저장 실패', { kind: 'csrf-unavailable' }, { kind: 'csrf-unavailable' }],
    ['network', { kind: 'network' }, { kind: 'transport' }],
    ['timeout', { kind: 'timeout' }, { kind: 'transport' }],
    ['2xx 형식 오류', { kind: 'invalid-response', status: 200 }, { kind: 'transport' }],
    ['429 Retry-After 있음', http(429, 'AUTH_OTP_RATE_LIMITED', 90), { kind: 'too-many', retryAfterSeconds: 90 }],
    ['429 Retry-After 없음', http(429, 'COMMON_TOO_MANY_REQUESTS'), { kind: 'too-many' }],
    ['503', http(503, 'MEMBER_OTP_DELIVERY_UNAVAILABLE'), { kind: 'unavailable' }],
    ['403 계정 사용 중지는 화면이 해석', http(403, 'MEMBER_ACCOUNT_DISABLED'), { kind: 'rejected', status: 403, code: 'MEMBER_ACCOUNT_DISABLED' }],
    ['그 밖의 403도 권한 없음 화면으로 보내지 않음', http(403), { kind: 'rejected', status: 403 }],
    ['401', http(401, 'AUTH_LOGIN_FAILED'), { kind: 'rejected', status: 401, code: 'AUTH_LOGIN_FAILED' }],
    ['500', http(500, 'COMMON_INTERNAL_ERROR'), { kind: 'rejected', status: 500, code: 'COMMON_INTERNAL_ERROR' }],
  ])('%s', (_name, failure, expected) => {
    expect(classifyFormFailure(failure)).toEqual(expected);
  });
});
