import type { ApiFailure } from './apiError';

/** Backend 인증 오류 코드(docs/global/module.md 2장, G-D003·G-D007). Backend에 없는 코드는 만들지 않는다. */
export const AUTH_UNAUTHENTICATED = 'AUTH_UNAUTHENTICATED';
export const AUTH_SESSION_STORE_UNAVAILABLE = 'AUTH_SESSION_STORE_UNAVAILABLE';
/** CSRF 토큰 누락·불일치(G-D014). 권한 문제가 아니다(FE-D021). */
export const AUTH_CSRF_INVALID = 'AUTH_CSRF_INVALID';

/** 403 AUTH_CSRF_INVALID인지. */
export function isCsrfInvalid(failure: ApiFailure): boolean {
  return failure.kind === 'http' && failure.status === 403 && failure.problem?.code === AUTH_CSRF_INVALID;
}

/** ProblemDetail 401 AUTH_UNAUTHENTICATED인지. */
export function isUnauthenticated(failure: ApiFailure): boolean {
  return failure.kind === 'http' && failure.status === 401 && failure.problem?.code === AUTH_UNAUTHENTICATED;
}

/** 503 AUTH_SESSION_STORE_UNAVAILABLE(세션 저장소 장애)인지. 인증 실패가 아니다. */
export function isSessionStoreUnavailable(failure: ApiFailure): boolean {
  return failure.kind === 'http' && failure.status === 503 && failure.problem?.code === AUTH_SESSION_STORE_UNAVAILABLE;
}
