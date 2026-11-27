import axios, { type AxiosInstance } from 'axios';
import { ApiError, type ApiFailure } from './apiError';
import { isCsrfInvalid, isUnauthenticated } from './authErrorCodes';
import { createCsrfTokenSource, CSRF_HEADER_NAME, isCsrfTargetAllowed, isUnsafeMethod } from './csrfToken';
import { parseProblemDetail } from './problemDetail';
import { ApiRequestError, DEFAULT_TIMEOUT_MS, resolveTimeoutMs, type ApiRequest } from './requestOptions';
import { combineSignals } from './signals';
import { API_BASE_PATH, toApiPath } from './urlGuard';

/**
 * 인스턴스 보안 기본값. 요청별로 바꿀 수 없다(FE-D013, FE-D014).
 * - Axios의 XSRF 자동 처리는 끈다(withXSRFToken === false면 Axios는 헤더를 붙이지 않는다). CSRF 헤더는 request()가
 *   상태 변경 요청에만 직접 붙인다(FE-D020, FE-D031).
 * - allowAbsoluteUrls: false면 baseURL을 항상 앞에 붙인다. 경로 검증(toApiPath)의 보조 장치다.
 */
export const AXIOS_SECURITY_DEFAULTS = {
  baseURL: API_BASE_PATH,
  allowAbsoluteUrls: false,
  withXSRFToken: false,
  xsrfCookieName: '',
  xsrfHeaderName: '',
  withCredentials: false,
  adapter: 'xhr',
} as const;

export type ResponseParser<T> = (data: unknown) => T | Promise<T>;

/**
 * shared/api가 인증 상태에 대해 아는 전부다. 구현은 features/auth가 붙인다(순환 의존 방지).
 */
export interface AuthBridge {
  /** 현재 인증 세대(FE-D011). */
  currentEpoch(): number;
  /** 그 세대의 session 요청이 쓸 signal. 세대가 바뀌면 abort된다. */
  sessionSignal(epoch: number): AbortSignal;
  /** session 요청이 401 AUTH_UNAUTHENTICATED를 받았다. 현재 상태와 세대 확인은 받는 쪽이 한다. */
  reportSessionLost(epoch: number): void;
}

export interface ApiClient {
  /** 실패는 ApiError(서버·전송), 요청 전 검증 실패는 ApiRequestError로 reject한다. */
  request<T = unknown>(request: ApiRequest, parse?: ResponseParser<T>): Promise<T>;
  /** 브리지는 하나만 붙는다. 돌려준 함수로 해제한다. */
  attachAuthBridge(bridge: AuthBridge): () => void;
}


const CSRF_TOKEN_PATH = '/v1/auth/csrf';

/** Retry-After를 1 이상의 안전한 정수 초로만 읽는다. 그 밖(누락·0·음수·소수·날짜·범위 초과)은 undefined(FE-D031). */
export function parseRetryAfterSeconds(value: unknown): number | undefined {
  if (typeof value !== 'string' || !/^\d+$/.test(value.trim())) return undefined;
  const seconds = Number(value.trim());
  return Number.isSafeInteger(seconds) && seconds >= 1 ? seconds : undefined;
}

function toFailure(error: unknown): ApiFailure {
  if (axios.isCancel(error)) return { kind: 'canceled' };
  if (!axios.isAxiosError(error)) return { kind: 'network' };
  if (error.code === 'ETIMEDOUT') return { kind: 'timeout' };
  const response = error.response;
  if (!response) return { kind: 'network' };
  const contentType = response.headers['content-type'];
  const retryAfterSeconds = parseRetryAfterSeconds(response.headers['retry-after']);
  return {
    kind: 'http',
    status: response.status,
    problem: parseProblemDetail(typeof contentType === 'string' ? contentType : undefined, response.data),
    ...(retryAfterSeconds === undefined ? {} : { retryAfterSeconds }),
  };
}

export function createApiClient(): ApiClient {
  // 원본 인스턴스는 이 함수 밖으로 내보내지 않는다(FE-D013).
  const instance: AxiosInstance = axios.create({
    ...AXIOS_SECURITY_DEFAULTS,
    transitional: { clarifyTimeoutError: true },
  });

  let bridge: AuthBridge | null = null;

  // 발급 API는 인증과 무관한 공개 GET이다(authScope none). 개별 요청의 취소와 묶지 않는다(FE-D031).
  const csrf = createCsrfTokenSource(async () => {
    try {
      await instance.request({ method: 'get', url: CSRF_TOKEN_PATH, timeout: DEFAULT_TIMEOUT_MS });
    } catch (error) {
      throw new ApiError(toFailure(error), { cause: error });
    }
  });

  /** 상태 변경 요청이면 CSRF 헤더를 만든다. 최종 URL이 같은 출처 /api/ 밖이면 요청하지 않는다. */
  async function csrfHeaders(request: ApiRequest, path: string, signal: AbortSignal | undefined) {
    if (!isUnsafeMethod(request.method)) return undefined;
    if (!isCsrfTargetAllowed(instance.getUri({ url: path }), window.location.origin)) {
      throw new ApiRequestError(`CSRF 토큰을 붙일 수 없는 요청 URL: ${JSON.stringify(path)}`);
    }
    return { [CSRF_HEADER_NAME]: await csrf.obtain(signal) };
  }

  return {
    attachAuthBridge(next: AuthBridge): () => void {
      if (bridge !== null) throw new Error('AuthBridge가 이미 연결되어 있다');
      bridge = next;
      return () => {
        if (bridge === next) bridge = null;
      };
    },

    async request<T>(request: ApiRequest, parse?: ResponseParser<T>): Promise<T> {
      const path = toApiPath(request.path);
      const timeout = resolveTimeoutMs(request.timeoutMs);

      // session 요청은 시작 세대를 기억하고 그 세대의 signal을 함께 쓴다(FE-D011).
      let session: { bridge: AuthBridge; epoch: number } | null = null;
      if (request.authScope === 'session') {
        if (bridge === null) throw new ApiRequestError('session 요청에는 AuthBridge 연결이 필요하다');
        session = { bridge, epoch: bridge.currentEpoch() };
      }
      const isStale = () => session !== null && session.bridge.currentEpoch() !== session.epoch;

      const combined = combineSignals([request.signal, session?.bridge.sessionSignal(session.epoch)]);
      let headers: Record<string, string> | undefined;
      try {
        headers = await csrfHeaders(request, path, combined.signal);
      } catch (error) {
        combined.release();
        if (error instanceof ApiRequestError) throw error;
        // 토큰을 기다리는 동안 세대가 바뀌었으면 보내지 않고 취소로 끝낸다(FE-D011).
        if (isStale()) throw new ApiError({ kind: 'canceled' }, { cause: error });
        throw error;
      }
      if (isStale()) {
        combined.release();
        throw new ApiError({ kind: 'canceled' });
      }
      let status: number;
      let data: unknown;
      try {
        const response = await instance.request({
          method: request.method,
          url: path,
          timeout,
          data: request.data,
          ...(headers === undefined ? {} : { headers }),
          ...(request.params === undefined ? {} : { params: request.params }),
          ...(combined.signal === undefined ? {} : { signal: combined.signal }),
        });
        status = response.status;
        data = response.data;
      } catch (error) {
        const failure = toFailure(error);
        // 원래 요청은 다시 보내지 않는다. 다음 상태 변경 요청 전에만 토큰을 다시 거친다(FE-D021, FE-D031).
        // CSRF 토큰은 인증 세대와 따로 산다. 세대가 바뀐 응답이어도 보낸 토큰이 아직 쿠키에 있으면 복구 정보를 남기고,
        // 쿠키가 이미 바뀌었으면 markInvalid가 무시한다. 응답 자체는 아래에서 canceled로 버린다.
        if (isCsrfInvalid(failure)) csrf.markInvalid(headers?.[CSRF_HEADER_NAME] ?? null);
        if (isStale()) throw new ApiError({ kind: 'canceled' }, { cause: error });
        if (session !== null && isUnauthenticated(failure)) {
          // Interceptor는 이동하지 않는다. 상태 갱신만 알리고 이동은 가드가 맡는다(FE-D010).
          session.bridge.reportSessionLost(session.epoch);
        }
        throw new ApiError(failure, { cause: error });
      } finally {
        combined.release();
      }
      if (isStale()) throw new ApiError({ kind: 'canceled' });
      if (!parse) return data as T;
      let parsed: T;
      try {
        // 비동기 parse도 기다려 형식 오류를 invalid-response로 바꾼다.
        parsed = await parse(data);
      } catch (error) {
        // 이미 버릴 오래된 세대의 응답이면 형식 오류가 아니라 취소다(FE-D011).
        if (isStale()) throw new ApiError({ kind: 'canceled' }, { cause: error });
        throw new ApiError({ kind: 'invalid-response', status }, { cause: error });
      }
      // 파싱하는 동안 세대가 바뀌었으면 결과를 버린다.
      if (isStale()) throw new ApiError({ kind: 'canceled' });
      return parsed;
    },
  };
}
