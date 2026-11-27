import { describe, expect, it } from 'vitest';
import { ApiError, type ApiFailure } from '../../shared/api/apiError';
import { classifyInitFailure } from './classifyInitFailure';

const problem = (code: string) => ({ code, fieldErrors: [] });
const api = (failure: ApiFailure) => new ApiError(failure);

// FE-T010: 초기화 중 ProblemDetail 401 AUTH_UNAUTHENTICATED만 미인증이다(FE-D009)
describe('classifyInitFailure', () => {
  it.each<[string, unknown, ReturnType<typeof classifyInitFailure>]>([
    ['401 AUTH_UNAUTHENTICATED', api({ kind: 'http', status: 401, problem: problem('AUTH_UNAUTHENTICATED') }), { type: 'unauthenticated' }],
    ['ProblemDetail이 아닌 401', api({ kind: 'http', status: 401, problem: null }), { type: 'failed', error: { kind: 'server', status: 401 } }],
    ['다른 code의 401', api({ kind: 'http', status: 401, problem: problem('SOMETHING') }), { type: 'failed', error: { kind: 'server', status: 401, code: 'SOMETHING' } }],
    ['503 세션 저장소 장애', api({ kind: 'http', status: 503, problem: problem('AUTH_SESSION_STORE_UNAVAILABLE') }), { type: 'failed', error: { kind: 'unavailable', status: 503, code: 'AUTH_SESSION_STORE_UNAVAILABLE' } }],
    ['code 없는 503', api({ kind: 'http', status: 503, problem: null }), { type: 'failed', error: { kind: 'server', status: 503 } }],
    ['500', api({ kind: 'http', status: 500, problem: problem('COMMON_INTERNAL_ERROR') }), { type: 'failed', error: { kind: 'server', status: 500, code: 'COMMON_INTERNAL_ERROR' } }],
    ['403', api({ kind: 'http', status: 403, problem: problem('AUTH_ACCESS_DENIED') }), { type: 'failed', error: { kind: 'server', status: 403, code: 'AUTH_ACCESS_DENIED' } }],
    ['network', api({ kind: 'network' }), { type: 'failed', error: { kind: 'network' } }],
    ['timeout', api({ kind: 'timeout' }), { type: 'failed', error: { kind: 'timeout' } }],
    ['2xx 형식 오류', api({ kind: 'invalid-response', status: 200 }), { type: 'failed', error: { kind: 'invalid-response', status: 200 } }],
    ['ApiError가 아닌 예외', new TypeError('bug'), { type: 'failed', error: { kind: 'unexpected' } }],
    // 초기화는 GET이라 생기지 않지만 생기더라도 미인증으로 보지 않는다(FE-D009, FE-D031)
    ['csrf-unavailable', api({ kind: 'csrf-unavailable' }), { type: 'failed', error: { kind: 'unexpected' } }],
    ['취소', api({ kind: 'canceled' }), { type: 'ignore' }],
  ])('%s', (_name, error, expected) => {
    expect(classifyInitFailure(error)).toEqual(expected);
  });
});
