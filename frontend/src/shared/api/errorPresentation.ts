import type { ApiFailure } from './apiError';
import { isSessionStoreUnavailable } from './authErrorCodes';

/**
 * 오류를 화면에서 어떻게 다룰지 고른다(FE-D010). 인증 상태 변경은 하지 않는다.
 * - forbidden: 권한 없음 화면
 * - retry-later: 503 AUTH_SESSION_STORE_UNAVAILABLE. 로그아웃하지 않고 재시도 안내
 * - common: 그 밖의 5xx·network·timeout·형식 오류 공통 안내
 * - caller: 400·401·404 등 호출한 화면이 맥락에 맞게 처리
 * - silent: 취소. 표시하지 않음
 */
export type FailurePresentation = 'forbidden' | 'retry-later' | 'common' | 'caller' | 'silent';

export function presentApiFailure(failure: ApiFailure): FailurePresentation {
  switch (failure.kind) {
    case 'canceled':
      return 'silent';
    case 'network':
    case 'timeout':
    case 'invalid-response':
      return 'common';
    case 'http':
      if (failure.status === 403) return 'forbidden';
      if (isSessionStoreUnavailable(failure)) return 'retry-later';
      return failure.status >= 500 ? 'common' : 'caller';
  }
}
