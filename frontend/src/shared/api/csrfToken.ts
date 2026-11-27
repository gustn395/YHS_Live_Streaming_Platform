import { ApiError } from './apiError';

/** G-D014: Backend가 내려 주는 CSRF 쿠키와 되돌려 보낼 헤더 이름. */
export const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
export const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

const UNSAFE_METHODS = new Set(['post', 'put', 'patch', 'delete']);

/** CSRF 헤더를 붙일 상태 변경 메서드인지(FE-D031). 그 밖의 메서드에는 붙이지 않는다. */
export function isUnsafeMethod(method: string): boolean {
  return UNSAFE_METHODS.has(method.toLowerCase());
}

/** 최종 요청 URL이 같은 출처의 `/api/` 아래인지(FE-D031). 아니면 토큰을 붙이지 않고 요청도 보내지 않는다. */
export function isCsrfTargetAllowed(url: string, origin: string): boolean {
  let resolved: URL;
  try {
    resolved = new URL(url, origin);
  } catch {
    return false;
  }
  return resolved.origin === origin && resolved.pathname.startsWith('/api/');
}

/** 현재 CSRF 쿠키 값. 없거나 비었으면 null. */
export function readCsrfCookie(): string | null {
  if (typeof document === 'undefined') return null;
  for (const part of document.cookie.split(';')) {
    const eq = part.indexOf('=');
    if (eq < 0 || part.slice(0, eq).trim() !== CSRF_COOKIE_NAME) continue;
    const raw = part.slice(eq + 1).trim();
    if (raw === '') return null;
    try {
      return decodeURIComponent(raw);
    } catch {
      return raw;
    }
  }
  return null;
}

/** 공유 Promise를 기다리되 자기 signal이 취소되면 자기만 빠진다. 공유 작업은 중단하지 않는다(FE-D031). */
function waitWithSignal(shared: Promise<void>, signal: AbortSignal | undefined): Promise<void> {
  if (signal === undefined) return shared;
  if (signal.aborted) return Promise.reject(new ApiError({ kind: 'canceled' }));
  return new Promise<void>((resolve, reject) => {
    const onAbort = () => reject(new ApiError({ kind: 'canceled' }));
    signal.addEventListener('abort', onAbort, { once: true });
    shared.then(
      () => {
        signal.removeEventListener('abort', onAbort);
        resolve();
      },
      (error: unknown) => {
        signal.removeEventListener('abort', onAbort);
        reject(error);
      },
    );
  });
}

export interface CsrfTokenSource {
  /** 붙일 토큰을 돌려준다. 쿠키가 없거나 복구가 필요하면 발급 API를 single-flight로 부른다. */
  obtain(signal?: AbortSignal): Promise<string>;
  /**
   * 보낸 토큰(sentToken, 없으면 null)으로 403 AUTH_CSRF_INVALID를 받았다. 쿠키가 아직 그 값이면 다음 obtain이 발급 API를
   * 거치게 한다. 쿠키가 이미 다른 값으로 바뀌었으면(예: 이전 인증 세대의 늦은 응답) 새 토큰에 영향을 주지 않는다(FE-D031).
   */
  markInvalid(sentToken: string | null): void;
}

/**
 * fetchToken은 발급 API(`GET /api/v1/auth/csrf`)를 호출하고 실패하면 ApiError로 reject한다.
 *
 * 복구 요구는 번호(invalidation)로 센다. 발급은 시작할 때의 번호를 기억하고, 성공하면 그 번호까지 복구된 것으로 본다.
 * 그래서 발급이 진행되는 동안 새 403이 오면 그 뒤의 요청은 진행 중 발급에 합류하지 않고, 그 발급이 끝난 뒤
 * 새 발급을 하나 더 이어 붙인다(이어 붙인 발급도 single-flight). 실패한 발급은 번호를 올리지 않아 복구 요구가 남는다.
 */
export function createCsrfTokenSource(fetchToken: () => Promise<void>): CsrfTokenSource {
  let invalidation = 0;
  let recovered = 0;
  let inflight: { promise: Promise<void>; covers: number } | null = null;

  function startFetch(): Promise<void> {
    const covers = invalidation;
    const previous = inflight?.promise.catch(() => undefined) ?? Promise.resolve();
    const promise: Promise<void> = previous
      .then(fetchToken)
      .then(() => {
        if (covers > recovered) recovered = covers;
      })
      .finally(() => {
        if (inflight?.promise === promise) inflight = null;
      });
    inflight = { promise, covers };
    return promise;
  }

  return {
    async obtain(signal) {
      if (recovered >= invalidation) {
        const existing = readCsrfCookie();
        if (existing !== null) return existing;
      }
      const shared = inflight !== null && inflight.covers >= invalidation ? inflight.promise : startFetch();
      await waitWithSignal(shared, signal);
      const issued = readCsrfCookie();
      if (issued === null) throw new ApiError({ kind: 'csrf-unavailable' });
      return issued;
    },
    markInvalid(sentToken) {
      const current = readCsrfCookie();
      if (current !== null && sentToken !== null && current !== sentToken) return;
      invalidation += 1;
    },
  };
}
