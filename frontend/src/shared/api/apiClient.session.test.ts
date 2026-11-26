import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw/server';
import { createApiClient, type AuthBridge } from './apiClient';
import { ApiError } from './apiError';
import { ApiRequestError, type AuthScope } from './requestOptions';

/** 테스트용 브리지: 세대와 세대별 AbortController를 직접 조작한다. */
function fakeBridge(initialEpoch = 1) {
  let epoch = initialEpoch;
  const controllers = new Map<number, AbortController>();
  const bridge: AuthBridge = {
    currentEpoch: () => epoch,
    sessionSignal: (e) => {
      let c = controllers.get(e);
      if (!c) {
        c = new AbortController();
        controllers.set(e, c);
      }
      return c.signal;
    },
    reportSessionLost: vi.fn(),
  };
  return {
    bridge,
    advance() {
      controllers.get(epoch)?.abort();
      epoch += 1;
    },
  };
}

async function failureOf(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error.failure;
    throw error;
  }
  throw new Error('요청이 성공했다');
}

function respond401(code: string | null) {
  server.use(
    http.get('*/api/v1/me', () =>
      code === null
        ? HttpResponse.json({ message: 'x' }, { status: 401 })
        : HttpResponse.json({ status: 401, title: 'Unauthorized', code }, { status: 401, headers: { 'Content-Type': 'application/problem+json' } }),
    ),
  );
}

describe('인증 브리지 연결', () => {
  it('session 요청은 브리지가 없으면 보내지 않는다', async () => {
    const client = createApiClient();
    await expect(client.request({ method: 'get', path: '/v1/me', authScope: 'session' })).rejects.toBeInstanceOf(
      ApiRequestError,
    );
  });

  it('브리지는 하나만 연결된다. 해제하면 다시 연결할 수 있다(FE-T014)', () => {
    const client = createApiClient();
    const detach = client.attachAuthBridge(fakeBridge().bridge);
    expect(() => client.attachAuthBridge(fakeBridge().bridge)).toThrow();
    detach();
    detach(); // 두 번 해제해도 다른 연결을 지우지 않는다
    const detachAgain = client.attachAuthBridge(fakeBridge().bridge);
    expect(() => client.attachAuthBridge(fakeBridge().bridge)).toThrow();
    detachAgain();
  });
});

// FE-T007: 401 판정 — 범위 × code × 세대(현재 인증 상태 판정은 features/auth의 sessionScope가 맡는다)
describe('401 판정', () => {
  const cases: Array<[AuthScope, string | null, boolean]> = [
    ['session', 'AUTH_UNAUTHENTICATED', true],
    ['session', 'AUTH_SESSION_STORE_UNAVAILABLE', false],
    ['session', 'SOMETHING_NEW', false],
    ['session', null, false],
    ['none', 'AUTH_UNAUTHENTICATED', false],
    ['initialization', 'AUTH_UNAUTHENTICATED', false],
  ];

  it.each(cases)('범위 %s, code %s → 세션 상실 보고 %s', async (authScope, code, expected) => {
    respond401(code);
    const client = createApiClient();
    const { bridge } = fakeBridge(7);
    client.attachAuthBridge(bridge);
    const failure = await failureOf(client.request({ method: 'get', path: '/v1/me', authScope }));
    expect(failure).toMatchObject({ kind: 'http', status: 401 });
    if (expected) {
      expect(bridge.reportSessionLost).toHaveBeenCalledTimes(1);
      expect(bridge.reportSessionLost).toHaveBeenCalledWith(7);
    } else {
      expect(bridge.reportSessionLost).not.toHaveBeenCalled();
    }
  });

  it('응답 전에 세대가 바뀐 401은 보고하지 않고 canceled로 버린다', async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    server.use(
      http.get('*/api/v1/me', async () => {
        await gate;
        return HttpResponse.json({ code: 'AUTH_UNAUTHENTICATED' }, { status: 401, headers: { 'Content-Type': 'application/problem+json' } });
      }),
    );
    const client = createApiClient();
    const fake = fakeBridge(3);
    // 세대 전환이 요청을 abort하지 않는 경우(네트워크 계층이 이미 응답을 받은 경우)를 흉내 내려고 signal을 끊는다
    const bridge: AuthBridge = { ...fake.bridge, sessionSignal: () => new AbortController().signal };
    client.attachAuthBridge(bridge);
    const pending = client.request({ method: 'get', path: '/v1/me', authScope: 'session' });
    await delay(10);
    fake.advance();
    release();
    expect(await failureOf(pending)).toEqual({ kind: 'canceled' });
    expect(fake.bridge.reportSessionLost).not.toHaveBeenCalled();
  });
});

// FE-T009: 오래된 세대의 성공 응답도 반영하지 않는다
describe('오래된 세대 응답', () => {
  it('session 요청 중 세대가 바뀌면 성공 응답도 canceled로 버린다', async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    server.use(
      http.get('*/api/v1/me', async () => {
        await gate;
        return HttpResponse.json({ id: 'old-member' });
      }),
    );
    const client = createApiClient();
    const fake = fakeBridge(1);
    const bridge: AuthBridge = { ...fake.bridge, sessionSignal: () => new AbortController().signal };
    client.attachAuthBridge(bridge);
    const pending = client.request({ method: 'get', path: '/v1/me', authScope: 'session' });
    await delay(10);
    fake.advance();
    release();
    expect(await failureOf(pending)).toEqual({ kind: 'canceled' });
  });
});

// FE-T009: 비동기 parse가 끝나기 전에 세대가 바뀌면 결과를 버린다(Bot 리뷰 반영)
describe('비동기 parse 중 세대 전환', () => {
  it('parse 완료 시점에 세대가 바뀌었으면 canceled', async () => {
    server.use(http.get('*/api/v1/me', () => HttpResponse.json({ id: 'old-member' })));
    const client = createApiClient();
    const fake = fakeBridge(1);
    client.attachAuthBridge(fake.bridge);
    const parse = async (data: unknown) => {
      fake.advance(); // 파싱하는 동안 세션이 바뀐다
      await delay(5);
      return data;
    };
    expect(await failureOf(client.request({ method: 'get', path: '/v1/me', authScope: 'session' }, parse))).toEqual({
      kind: 'canceled',
    });
  });

  it('세대가 바뀐 뒤 parse가 reject해도 형식 오류가 아니라 canceled(FE-D011)', async () => {
    server.use(http.get('*/api/v1/me', () => HttpResponse.json({ id: 'old-member' })));
    const client = createApiClient();
    const fake = fakeBridge(1);
    client.attachAuthBridge(fake.bridge);
    const parse = async () => {
      fake.advance();
      await delay(5);
      throw new Error('형식 오류');
    };
    expect(await failureOf(client.request({ method: 'get', path: '/v1/me', authScope: 'session' }, parse))).toEqual({
      kind: 'canceled',
    });
  });
});

// FE-T011: 세대 전환은 이전 세대의 session 요청만 취소한다
describe('선택적 취소', () => {
  function slowEndpoint() {
    server.use(
      http.get('*/api/v1/slow', async () => {
        await delay(200);
        return HttpResponse.json({ ok: true });
      }),
    );
  }

  it('세대가 바뀌면 이전 세대 session 요청은 canceled, none 요청은 계속된다', async () => {
    slowEndpoint();
    const client = createApiClient();
    const fake = fakeBridge(1);
    client.attachAuthBridge(fake.bridge);
    const sessionReq = client.request({ method: 'get', path: '/v1/slow', authScope: 'session' });
    const publicReq = client.request({ method: 'get', path: '/v1/slow', authScope: 'none' });
    await delay(10);
    fake.advance();
    expect(await failureOf(sessionReq)).toEqual({ kind: 'canceled' });
    await expect(publicReq).resolves.toEqual({ ok: true });
    expect(fake.bridge.reportSessionLost).not.toHaveBeenCalled();
  });

  it('session 요청도 호출자 signal로 취소할 수 있다', async () => {
    slowEndpoint();
    const client = createApiClient();
    client.attachAuthBridge(fakeBridge(1).bridge);
    const controller = new AbortController();
    const pending = client.request({ method: 'get', path: '/v1/slow', authScope: 'session', signal: controller.signal });
    await delay(10);
    controller.abort();
    expect(await failureOf(pending)).toEqual({ kind: 'canceled' });
  });
});
