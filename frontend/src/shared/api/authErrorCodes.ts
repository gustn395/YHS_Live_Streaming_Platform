import type { ApiFailure } from './apiError';

/** Backend 인증 오류 코드(docs/global/module.md 2장, G-D003·G-D007). Backend에 없는 코드는 만들지 않는다. */
export const AUTH_UNAUTHENTICATED = 'AUTH_UNAUTHENTICATED';
export const AUTH_SESSION_STORE_UNAVAILABLE = 'AUTH_SESSION_STORE_UNAVAILABLE';

/** ProblemDetail 401 AUTH_UNAUTHENTICATED인지. */
export function isUnauthenticated(failure: ApiFailure): boolean {
  return failure.kind === 'http' && failure.status === 401 && failure.problem?.code === AUTH_UNAUTHENTICATED;
}

/** 503 AUTH_SESSION_STORE_UNAVAILABLE(세션 저장소 장애)인지. 인증 실패가 아니다. */
export function isSessionStoreUnavailable(failure: ApiFailure): boolean {
  return failure.kind === 'http' && failure.status === 503 && failure.problem?.code === AUTH_SESSION_STORE_UNAVAILABLE;
}
