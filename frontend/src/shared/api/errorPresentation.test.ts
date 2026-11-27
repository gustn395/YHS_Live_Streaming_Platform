import { describe, expect, it } from 'vitest';
import type { ApiFailure } from './apiError';
import { presentApiFailure } from './errorPresentation';

const problem = (code: string) => ({ code, fieldErrors: [] });

// FE-D010: 403 권한 없음, 503 AUTH_SESSION_STORE_UNAVAILABLE 재시도 안내(로그아웃 없음), 기타 5xx·network 공통 오류, 취소는 표시 안 함
describe('presentApiFailure', () => {
  it.each<[string, ApiFailure, ReturnType<typeof presentApiFailure>]>([
    ['403', { kind: 'http', status: 403, problem: problem('AUTH_ACCESS_DENIED') }, 'forbidden'],
    // FE-T022: CSRF 403은 권한 없음이 아니라 호출 화면이 처리한다(FE-D021)
    ['403 CSRF', { kind: 'http', status: 403, problem: problem('AUTH_CSRF_INVALID') }, 'csrf-retry'],
    ['code 없는 403', { kind: 'http', status: 403, problem: null }, 'forbidden'],
    ['csrf-unavailable', { kind: 'csrf-unavailable' }, 'common'],
    ['503 세션 저장소', { kind: 'http', status: 503, problem: problem('AUTH_SESSION_STORE_UNAVAILABLE') }, 'retry-later'],
    ['code 없는 503', { kind: 'http', status: 503, problem: null }, 'common'],
    ['500', { kind: 'http', status: 500, problem: problem('COMMON_INTERNAL_ERROR') }, 'common'],
    ['network', { kind: 'network' }, 'common'],
    ['timeout', { kind: 'timeout' }, 'common'],
    ['형식 오류', { kind: 'invalid-response', status: 200 }, 'common'],
    ['400 입력 오류', { kind: 'http', status: 400, problem: problem('COMMON_INVALID_INPUT') }, 'caller'],
    ['401', { kind: 'http', status: 401, problem: problem('AUTH_UNAUTHENTICATED') }, 'caller'],
    ['취소', { kind: 'canceled' }, 'silent'],
  ])('%s → %s', (_name, failure, expected) => {
    expect(presentApiFailure(failure)).toBe(expected);
  });
});
