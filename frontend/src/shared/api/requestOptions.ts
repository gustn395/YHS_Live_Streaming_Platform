/** 요청이 현재 인증과 어떤 관계인지. 401을 어떻게 해석할지 정한다(FE-D010). 기본값은 없다. */
export type AuthScope = 'none' | 'initialization' | 'session';

export type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete';

export type QueryValue = string | number | boolean;

/**
 * 공통 Client가 받는 요청 옵션 전부다. Axios 옵션(baseURL, xsrf*, adapter 등)은 받지 않는다(FE-D013).
 */
export interface ApiRequest {
  method: HttpMethod;
  /** `/api` 뒤의 경로. `/`로 시작해야 한다. 예: `/v1/members` */
  path: string;
  authScope: AuthScope;
  /** 1~60000 정수(ms). 없으면 기본 10초(FE-D007). */
  timeoutMs?: number;
  signal?: AbortSignal;
  data?: unknown;
  params?: Readonly<Record<string, QueryValue>>;
}

/** 요청을 보내기 전에 잡은 호출 코드의 잘못. 서버 응답 오류(ApiError)와 구분한다. */
export class ApiRequestError extends Error {
  override readonly name = 'ApiRequestError';
}

export const DEFAULT_TIMEOUT_MS = 10_000;
export const MIN_TIMEOUT_MS = 1;
export const MAX_TIMEOUT_MS = 60_000;

export function resolveTimeoutMs(timeoutMs: number | undefined): number {
  if (timeoutMs === undefined) return DEFAULT_TIMEOUT_MS;
  if (!Number.isInteger(timeoutMs) || timeoutMs < MIN_TIMEOUT_MS || timeoutMs > MAX_TIMEOUT_MS) {
    throw new ApiRequestError(`timeoutMs는 ${MIN_TIMEOUT_MS}~${MAX_TIMEOUT_MS} 정수여야 한다: ${String(timeoutMs)}`);
  }
  return timeoutMs;
}
