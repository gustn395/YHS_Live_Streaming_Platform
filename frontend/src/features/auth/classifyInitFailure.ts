import { isApiError } from '../../shared/api/apiError';
import { isSessionStoreUnavailable, isUnauthenticated } from '../../shared/api/authErrorCodes';
import type { InitError } from './authState';

export type InitFailureOutcome = { type: 'unauthenticated' } | { type: 'failed'; error: InitError } | { type: 'ignore' };

/**
 * 초기화 실패를 분류한다(FE-D009). ProblemDetail 401 AUTH_UNAUTHENTICATED만 미인증이고,
 * 503·5xx·network·timeout·형식 오류는 미인증이 아니다. 취소는 호출한 쪽이 처리하므로 무시한다.
 */
export function classifyInitFailure(error: unknown): InitFailureOutcome {
  if (!isApiError(error)) return { type: 'failed', error: { kind: 'unexpected' } };
  const failure = error.failure;
  switch (failure.kind) {
    case 'canceled':
      return { type: 'ignore' };
    case 'network':
    case 'timeout':
      return { type: 'failed', error: { kind: failure.kind } };
    case 'invalid-response':
      return { type: 'failed', error: { kind: 'invalid-response', status: failure.status } };
    case 'http': {
      const code = failure.problem?.code;
      if (isUnauthenticated(failure)) return { type: 'unauthenticated' };
      const kind = isSessionStoreUnavailable(failure) ? 'unavailable' : 'server';
      return { type: 'failed', error: code === undefined ? { kind, status: failure.status } : { kind, status: failure.status, code } };
    }
  }
}
