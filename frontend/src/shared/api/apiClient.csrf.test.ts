import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw/server';
import { createApiClient, type AuthBridge } from './apiClient';
import { ApiError, type ApiFailure } from './apiError';
import { isCsrfTargetAllowed } from './csrfToken';
import type { HttpMethod } from './requestOptions';

const PROBLEM_JSON = { 'Content-Type': 'application/problem+json' };
const CSRF_PATH = '*/api/v1/auth/csrf';

async function failureOf(promise: Promise<unknown>): Promise<ApiFailure> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error.failure;
    throw error;
  }
  throw new Error('요청이 성공했다');
}

/** 요청마다 받은 X-XSRF-TOKEN 값(없으면 null)을 기록한다. */
function recordWrites(method: 'post' | 'put' | 'patch' | 'delete' | 'get' | 'head' | 'options' = 'post') {
  const seen: (string | null)[] = [];
  server.use(
    http[method]('*/api/v1/things', ({ request }) => {
      seen.push(request.headers.get('x-xsrf-token'));
      return method === 'head' || method === 'options' ? new HttpResponse(null, { status: 204 }) : HttpResponse.json({ ok: true });
    }),
  );
  return seen;
}

/** /csrf 응답을 테스트가 직접 끝내게 한다. 호출 수를 센다. */
function controlledCsrf(setCookie = true) {
  let calls = 0;
  let release: () => void = () => {};
  let fail = false;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  server.use(
    http.get(CSRF_PATH, async () => {
      calls += 1;
      await gate;
      if (fail) return HttpResponse.json({ code: 'COMMON_INTERNAL_ERROR' }, { status: 500, headers: PROBLEM_JSON });
      if (setCookie) document.cookie = 'XSRF-TOKEN=issued-token; path=/';
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return {
    calls: () => calls,
    release,
    failNext() {
      fail = true;
    },
  };
}

// FE-T019: 상태 변경 메서드에만 쿠키 값을 X-XSRF-TOKEN으로 붙인다(FE-D020, FE-D031)
describe('CSRF 헤더 부착 범위', () => {
  it.each(['post', 'put', 'patch', 'delete'] as const)('%s 요청에는 쿠키 값을 헤더로 붙인다', async (method) => {
    document.cookie = 'XSRF-TOKEN=cookie-token; path=/';
    const seen = recordWrites(method);
    await createApiClient().request({ method, path: '/v1/things', authScope: 'none' });
    expect(seen).toEqual(['cookie-token']);
  });

  it.each(['get', 'head', 'options'] as const)('%s 요청에는 쿠키가 있어도 헤더를 붙이지 않고 /csrf도 부르지 않는다', async (method) => {
    document.cookie = 'XSRF-TOKEN=cookie-token; path=/';
    const csrf = controlledCsrf();
    const seen = recordWrites(method);
    // HEAD·OPTIONS는 ApiRequest 타입이 허용하지 않지만 런타임에서도 붙지 않는지 확인한다
    await createApiClient().request({ method: method as HttpMethod, path: '/v1/things', authScope: 'none' });
    expect(seen).toEqual([null]);
    expect(csrf.calls()).toBe(0);
  });

  it('최종 URL이 같은 출처의 /api/ 아래일 때만 부착 대상이다', () => {
    const origin = 'http://localhost:3000';
    expect(isCsrfTargetAllowed('http://localhost:3000/api/v1/auth/signup', origin)).toBe(true);
    expect(isCsrfTargetAllowed('/api/v1/auth/signup', origin)).toBe(true);
    expect(isCsrfTargetAllowed('https://evil.example/api/v1/x', origin)).toBe(false);
    expect(isCsrfTargetAllowed('//evil.example/api/v1/x', origin)).toBe(false);
    expect(isCsrfTargetAllowed('http://localhost:3001/api/v1/x', origin)).toBe(false);
    expect(isCsrfTargetAllowed('/apix/v1/x', origin)).toBe(false);
    expect(isCsrfTargetAllowed('/api', origin)).toBe(false);
  });
});

// FE-T020: 토큰 확보(FE-D020, FE-D031)
describe('CSRF 토큰 확보', () => {
  it('쿠키가 있으면 /csrf를 부르지 않는다', async () => {
    document.cookie = 'XSRF-TOKEN=cookie-token; path=/';
    const csrf = controlledCsrf();
    recordWrites();
    await createApiClient().request({ method: 'post', path: '/v1/things', authScope: 'none' });
    expect(csrf.calls()).toBe(0);
  });

  it('쿠키가 없으면 동시 요청 3건에 /csrf를 한 번만 부르고 발급된 값을 붙인다', async () => {
    const csrf = controlledCsrf();
    const seen = recordWrites();
    const client = createApiClient();
    const requests = [1, 2, 3].map(() => client.request({ method: 'post', path: '/v1/things', authScope: 'none' }));
    await vi.waitFor(() => expect(csrf.calls()).toBe(1));
    csrf.release();
    await Promise.all(requests);
    expect(csrf.calls()).toBe(1);
    expect(seen).toEqual(['issued-token', 'issued-token', 'issued-token']);
  });

  it('/csrf가 실패하면 기다리던 요청이 모두 같은 오류로 실패하고 아무것도 보내지 않는다', async () => {
    const csrf = controlledCsrf();
    csrf.failNext();
    const seen = recordWrites();
    const client = createApiClient();
    const requests = [1, 2].map(() => failureOf(client.request({ method: 'post', path: '/v1/things', authScope: 'none' })));
    await vi.waitFor(() => expect(csrf.calls()).toBe(1));
    csrf.release();
    const failures = await Promise.all(requests);
    expect(failures.map((f) => f.kind)).toEqual(['http', 'http']);
    expect(seen).toEqual([]);
  });

  it('실패한 뒤 다음 요청은 /csrf를 다시 부른다', async () => {
    let calls = 0;
    server.use(
      http.get(CSRF_PATH, () => {
        calls += 1;
        if (calls === 1) return HttpResponse.json({ code: 'COMMON_INTERNAL_ERROR' }, { status: 500, headers: PROBLEM_JSON });
        document.cookie = 'XSRF-TOKEN=second; path=/';
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const seen = recordWrites();
    const client = createApiClient();
    await failureOf(client.request({ method: 'post', path: '/v1/things', authScope: 'none' }));
    await client.request({ method: 'post', path: '/v1/things', authScope: 'none' });
    expect(calls).toBe(2);
    expect(seen).toEqual(['second']);
  });

  it('/csrf가 성공했는데 쿠키가 없으면 csrf-unavailable이고 원래 요청을 보내지 않는다', async () => {
    const csrf = controlledCsrf(false);
    const seen = recordWrites();
    const pending = failureOf(createApiClient().request({ method: 'post', path: '/v1/things', authScope: 'none' }));
    csrf.release();
    expect(await pending).toEqual({ kind: 'csrf-unavailable' });
    expect(seen).toEqual([]);
  });
});

// FE-T021: 토큰을 기다리는 동안의 취소·세대(FE-D011, FE-D031)
describe('CSRF 대기 중 취소', () => {
  it('기다리던 요청 하나를 취소하면 그 요청만 보내지 않고 나머지는 보낸다', async () => {
    const csrf = controlledCsrf();
    const seen = recordWrites();
    const client = createApiClient();
    const controller = new AbortController();
    const canceled = failureOf(client.request({ method: 'post', path: '/v1/things', authScope: 'none', signal: controller.signal }));
    const kept = client.request({ method: 'post', path: '/v1/things', authScope: 'none' });
    await vi.waitFor(() => expect(csrf.calls()).toBe(1));
    controller.abort();
    expect(await canceled).toEqual({ kind: 'canceled' });
    csrf.release();
    await kept;
    expect(seen).toEqual(['issued-token']);
    expect(csrf.calls()).toBe(1);
  });

  it('기다리는 동안 인증 세대가 바뀌면 session 요청을 보내지 않는다', async () => {
    let epoch = 1;
    const controllers = new Map<number, AbortController>();
    const bridge: AuthBridge = {
      currentEpoch: () => epoch,
      sessionSignal: (e) => {
        if (!controllers.has(e)) controllers.set(e, new AbortController());
        return controllers.get(e)!.signal;
      },
      reportSessionLost: vi.fn(),
    };
    const csrf = controlledCsrf();
    const seen = recordWrites();
    const client = createApiClient();
    client.attachAuthBridge(bridge);
    const pending = failureOf(client.request({ method: 'post', path: '/v1/things', authScope: 'session' }));
    await vi.waitFor(() => expect(csrf.calls()).toBe(1));
    controllers.get(1)!.abort();
    epoch = 2;
    csrf.release();
    expect(await pending).toEqual({ kind: 'canceled' });
    expect(seen).toEqual([]);
  });
});

// FE-T022: CSRF 403 뒤에는 자동 재전송하지 않고, 다음 상태 변경 요청 전에 /csrf를 한 번 다시 거친다(FE-D021, FE-D031)
describe('CSRF 403 이후', () => {
  it('403 AUTH_CSRF_INVALID는 재전송하지 않고 다음 요청 전에 쿠키가 있어도 /csrf를 부른다', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/';
    let writes = 0;
    const tokens: (string | null)[] = [];
    server.use(
      http.post('*/api/v1/things', ({ request }) => {
        writes += 1;
        tokens.push(request.headers.get('x-xsrf-token'));
        if (writes === 1) return HttpResponse.json({ code: 'AUTH_CSRF_INVALID' }, { status: 403, headers: PROBLEM_JSON });
        return HttpResponse.json({ ok: true });
      }),
    );
    let csrfCalls = 0;
    server.use(
      http.get(CSRF_PATH, () => {
        csrfCalls += 1;
        document.cookie = 'XSRF-TOKEN=fresh; path=/';
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const client = createApiClient();
    const failure = await failureOf(client.request({ method: 'post', path: '/v1/things', authScope: 'none' }));
    expect(failure).toMatchObject({ kind: 'http', status: 403 });
    expect(writes).toBe(1);
    expect(csrfCalls).toBe(0);
    await client.request({ method: 'post', path: '/v1/things', authScope: 'none' });
    expect(csrfCalls).toBe(1);
    expect(writes).toBe(2);
    expect(tokens).toEqual(['stale', 'fresh']);
    // 강제 재확보가 성공한 뒤에는 다시 부르지 않는다
    await client.request({ method: 'post', path: '/v1/things', authScope: 'none' });
    expect(csrfCalls).toBe(1);
  });

  it('이전 인증 세대 요청의 403이어도 보낸 토큰이 그대로면 다음 요청 전에 /csrf를 부른다(응답은 canceled)', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/';
    let epoch = 1;
    const controllers = new Map<number, AbortController>();
    const bridge: AuthBridge = {
      currentEpoch: () => epoch,
      sessionSignal: (e) => {
        if (!controllers.has(e)) controllers.set(e, new AbortController());
        return controllers.get(e)!.signal;
      },
      reportSessionLost: vi.fn(),
    };
    let release: () => void = () => {};
    let writes = 0;
    server.use(
      http.post('*/api/v1/things', async () => {
        writes += 1;
        if (writes === 1) {
          await new Promise<void>((r) => (release = r));
          return HttpResponse.json({ code: 'AUTH_CSRF_INVALID' }, { status: 403, headers: PROBLEM_JSON });
        }
        return HttpResponse.json({ ok: true });
      }),
    );
    let csrfCalls = 0;
    server.use(
      http.get(CSRF_PATH, () => {
        csrfCalls += 1;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const client = createApiClient();
    client.attachAuthBridge(bridge);
    const pending = failureOf(client.request({ method: 'post', path: '/v1/things', authScope: 'session' }));
    await vi.waitFor(() => expect(writes).toBe(1));
    epoch = 2; // 세대만 바뀌고 응답은 계속 진행된다(세대 signal은 abort하지 않음)
    release();
    expect(await pending).toEqual({ kind: 'canceled' });
    await client.request({ method: 'post', path: '/v1/things', authScope: 'none' });
    expect(csrfCalls).toBe(1);
  });
});

// FE-T023: Retry-After는 1 이상의 안전한 정수 초만 쓴다(FE-D025, FE-D031)
describe('Retry-After', () => {
  it.each<[string, string | null, number | undefined]>([
    ['정수', '120', 120],
    ['1', '1', 1],
    ['누락', null, undefined],
    ['0', '0', undefined],
    ['음수', '-5', undefined],
    ['소수', '1.5', undefined],
    ['문자', 'soon', undefined],
    ['HTTP-date', 'Wed, 21 Oct 2026 07:28:00 GMT', undefined],
    ['안전 정수 초과', '9007199254740993', undefined],
  ])('%s → %s', async (_name, header, expected) => {
    document.cookie = 'XSRF-TOKEN=t; path=/';
    server.use(
      http.post('*/api/v1/things', () =>
        HttpResponse.json(
          { code: 'COMMON_TOO_MANY_REQUESTS' },
          { status: 429, headers: { ...PROBLEM_JSON, ...(header === null ? {} : { 'Retry-After': header }) } },
        ),
      ),
    );
    const failure = await failureOf(createApiClient().request({ method: 'post', path: '/v1/things', authScope: 'none' }));
    expect(failure.kind).toBe('http');
    expect(failure.kind === 'http' ? failure.retryAfterSeconds : 'x').toBe(expected);
  });
});
