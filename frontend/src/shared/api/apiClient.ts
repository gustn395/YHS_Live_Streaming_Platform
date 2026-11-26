import axios, { type AxiosInstance } from 'axios';
import { ApiError, type ApiFailure } from './apiError';
import { isUnauthenticated } from './authErrorCodes';
import { parseProblemDetail } from './problemDetail';
import { ApiRequestError, resolveTimeoutMs, type ApiRequest } from './requestOptions';
import { combineSignals } from './signals';
import { API_BASE_PATH, toApiPath } from './urlGuard';

/**
 * 인스턴스 보안 기본값. 요청별로 바꿀 수 없다(FE-D013, FE-D014).
 * - XSRF 자동 처리는 G-U02 확정 전까지 끈다(Axios는 withXSRFToken === false면 헤더를 붙이지 않는다).
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

export type ResponseParser<T> = (data: unknown) => T;

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


function toFailure(error: unknown): ApiFailure {
  if (axios.isCancel(error)) return { kind: 'canceled' };
  if (!axios.isAxiosError(error)) return { kind: 'network' };
  if (error.code === 'ETIMEDOUT') return { kind: 'timeout' };
  const response = error.response;
  if (!response) return { kind: 'network' };
  const contentType = response.headers['content-type'];
  return {
    kind: 'http',
    status: response.status,
    problem: parseProblemDetail(typeof contentType === 'string' ? contentType : undefined, response.data),
  };
}

export function createApiClient(): ApiClient {
  // 원본 인스턴스는 이 함수 밖으로 내보내지 않는다(FE-D013).
  const instance: AxiosInstance = axios.create({
    ...AXIOS_SECURITY_DEFAULTS,
    transitional: { clarifyTimeoutError: true },
  });

  let bridge: AuthBridge | null = null;

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
      let status: number;
      let data: unknown;
      try {
        const response = await instance.request({
          method: request.method,
          url: path,
          timeout,
          data: request.data,
          ...(request.params === undefined ? {} : { params: request.params }),
          ...(combined.signal === undefined ? {} : { signal: combined.signal }),
        });
        status = response.status;
        data = response.data;
      } catch (error) {
        if (isStale()) throw new ApiError({ kind: 'canceled' }, { cause: error });
        const failure = toFailure(error);
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
      try {
        return parse(data);
      } catch (error) {
        throw new ApiError({ kind: 'invalid-response', status }, { cause: error });
      }
    },
  };
}
