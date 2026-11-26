import { act, render, screen, waitFor } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { StrictMode, useEffect, type ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { createApiClient, type ApiClient } from '../../shared/api/apiClient';
import { ApiError } from '../../shared/api/apiError';
import { server } from '../../test/msw/server';
import { createProvisionalAuthAdapter, PROVISIONAL_ME_PATH } from '../../test/provisionalAuthAdapter';
import type { AuthAdapter } from './authAdapter';
import { AuthProvider } from './AuthProvider';
import type { AuthState } from './authState';
import { unavailableAuthAdapter } from './unavailableAuthAdapter';
import { useAuth } from './useAuth';

const PROBLEM = { 'Content-Type': 'application/problem+json' };
const ME = `*/api${PROVISIONAL_ME_PATH}`;

/** 상태 변화를 순서대로 기록한다. */
function Probe({ log }: { log: AuthState<unknown>[] }) {
  const { state, retry } = useAuth();
  if (log.at(-1) !== state) log.push(state);
  return (
    <div>
      <span data-testid="status">{state.status}</span>
      <span data-testid="error">{state.status === 'initializing' ? (state.initError?.kind ?? '') : ''}</span>
      <button type="button" onClick={retry}>
        retry
      </button>
    </div>
  );
}

function renderAuth(opts: { adapter: AuthAdapter<unknown>; client?: ApiClient; deadlineMs?: number; onSessionLost?: () => void; strict?: boolean }) {
  const log: AuthState<unknown>[] = [];
  const client = opts.client ?? createApiClient();
  const tree: ReactNode = (
    <AuthProvider
      adapter={opts.adapter}
      client={client}
      {...(opts.deadlineMs === undefined ? {} : { deadlineMs: opts.deadlineMs })}
      {...(opts.onSessionLost === undefined ? {} : { onSessionLost: opts.onSessionLost })}
    >
      <Probe log={log} />
    </AuthProvider>
  );
  const utils = render(opts.strict ? <StrictMode>{tree}</StrictMode> : tree);
  return { ...utils, log, client };
}

const status = () => screen.getByTestId('status').textContent;
const errorKind = () => screen.getByTestId('error').textContent;

// FE-T015(일부): 운영 어댑터는 네트워크 없이 항상 미인증이다(FE-D005)
describe('운영 어댑터', () => {
  it('네트워크 요청 없이 unauthenticated가 된다', async () => {
    const xhrOpen = vi.spyOn(XMLHttpRequest.prototype, 'open');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    renderAuth({ adapter: unavailableAuthAdapter });
    await waitFor(() => expect(status()).toBe('unauthenticated'));
    expect(xhrOpen).not.toHaveBeenCalled();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('인증 성공 결과를 돌려주는 경로가 없다', async () => {
    const results = await Promise.all(
      Array.from({ length: 5 }, () => unavailableAuthAdapter.initialize({ signal: new AbortController().signal })),
    );
    expect(results.every((r) => r.authenticated === false)).toBe(true);
  });
});

// FE-T010: 초기화 결과 분류(가안 MSW 응답)
describe('초기화', () => {
  it('가안 회원 조회 성공 → authenticated', async () => {
    server.use(http.get(ME, () => HttpResponse.json({ memberId: 'm-1' })));
    const client = createApiClient();
    renderAuth({ adapter: createProvisionalAuthAdapter(client), client });
    await waitFor(() => expect(status()).toBe('authenticated'));
  });

  it('401 AUTH_UNAUTHENTICATED → unauthenticated', async () => {
    server.use(http.get(ME, () => HttpResponse.json({ status: 401, code: 'AUTH_UNAUTHENTICATED' }, { status: 401, headers: PROBLEM })));
    const client = createApiClient();
    renderAuth({ adapter: createProvisionalAuthAdapter(client), client });
    await waitFor(() => expect(status()).toBe('unauthenticated'));
  });

  it.each([
    ['503 세션 저장소 장애', () => HttpResponse.json({ code: 'AUTH_SESSION_STORE_UNAVAILABLE' }, { status: 503, headers: PROBLEM }), 'unavailable'],
    ['500', () => HttpResponse.json({ code: 'COMMON_INTERNAL_ERROR' }, { status: 500, headers: PROBLEM }), 'server'],
    ['네트워크', () => HttpResponse.error(), 'network'],
    ['2xx 형식 오류', () => HttpResponse.json({ nope: true }), 'invalid-response'],
  ])('%s → 미인증이 아니라 initError(이동 없음)', async (_name, resolver, kind) => {
    server.use(http.get(ME, resolver));
    const client = createApiClient();
    renderAuth({ adapter: createProvisionalAuthAdapter(client), client });
    await waitFor(() => expect(errorKind()).toBe(kind));
    expect(status()).toBe('initializing');
  });

  it('HTTP Timeout → initError timeout', async () => {
    const adapter: AuthAdapter<unknown> = { initialize: () => Promise.reject(new ApiError({ kind: 'timeout' })) };
    renderAuth({ adapter });
    await waitFor(() => expect(errorKind()).toBe('timeout'));
  });

  it('재시도하면 initError를 지우고 최신 결과를 반영한다', async () => {
    let calls = 0;
    server.use(
      http.get(ME, () => {
        calls += 1;
        return calls === 1
          ? HttpResponse.json({ code: 'AUTH_SESSION_STORE_UNAVAILABLE' }, { status: 503, headers: PROBLEM })
          : HttpResponse.json({ memberId: 'm-1' });
      }),
    );
    const client = createApiClient();
    renderAuth({ adapter: createProvisionalAuthAdapter(client), client });
    await waitFor(() => expect(errorKind()).toBe('unavailable'));
    act(() => screen.getByRole('button', { name: 'retry' }).click());
    expect(errorKind()).toBe('');
    await waitFor(() => expect(status()).toBe('authenticated'));
  });

  it('초기화 전체 Deadline을 넘기면 initError(deadline), 요청을 취소하고 늦은 응답은 무시한다', async () => {
    let seenSignal: AbortSignal | undefined;
    let resolveLate: (v: { authenticated: true; principal: unknown }) => void = () => {};
    const adapter: AuthAdapter<unknown> = {
      initialize: ({ signal }) => {
        seenSignal = signal;
        return new Promise((resolve) => (resolveLate = resolve));
      },
    };
    const { log } = renderAuth({ adapter, deadlineMs: 30 });
    await waitFor(() => expect(errorKind()).toBe('deadline'));
    expect(seenSignal?.aborted).toBe(true);
    await act(async () => {
      resolveLate({ authenticated: true, principal: { id: 'late' } });
      await delay(10);
    });
    expect(status()).toBe('initializing');
    expect(errorKind()).toBe('deadline');
    expect(log.some((s) => s.status === 'authenticated')).toBe(false);
  });

  it('Deadline 뒤 늦게 온 실패도 initError를 덮어쓰지 않는다', async () => {
    let rejectLate: (e: unknown) => void = () => {};
    const adapter: AuthAdapter<unknown> = { initialize: () => new Promise((_r, reject) => (rejectLate = reject)) };
    renderAuth({ adapter, deadlineMs: 30 });
    await waitFor(() => expect(errorKind()).toBe('deadline'));
    await act(async () => {
      rejectLate(new ApiError({ kind: 'http', status: 401, problem: { code: 'AUTH_UNAUTHENTICATED', fieldErrors: [] } }));
      await delay(10);
    });
    expect(status()).toBe('initializing');
    expect(errorKind()).toBe('deadline');
  });

  it('기본 Deadline은 15초다', async () => {
    const { AUTH_INIT_DEADLINE_MS } = await import('./AuthProvider');
    expect(AUTH_INIT_DEADLINE_MS).toBe(15_000);
  });
});

// FE-T009·FE-T014: StrictMode에서 이전 초기화 결과가 늦게 와도 최신 결과만 반영한다
describe('StrictMode', () => {
  it('먼저 시작한 초기화가 늦게 성공해도 최신(미인증) 결과를 유지하고, 첫 요청은 취소된다', async () => {
    const resolvers: Array<(v: { authenticated: boolean; principal?: unknown }) => void> = [];
    const signals: AbortSignal[] = [];
    const adapter: AuthAdapter<unknown> = {
      initialize: ({ signal }) => {
        signals.push(signal);
        return new Promise((resolve) => resolvers.push(resolve as never));
      },
    };
    const { log } = renderAuth({ adapter, strict: true });
    await waitFor(() => expect(resolvers.length).toBe(2));
    expect(signals[0]?.aborted).toBe(true);
    await act(async () => {
      resolvers[1]?.({ authenticated: false });
      await delay(5);
      resolvers[0]?.({ authenticated: true, principal: { id: 'stale' } });
      await delay(5);
    });
    expect(status()).toBe('unauthenticated');
    expect(log.some((s) => s.status === 'authenticated')).toBe(false);
  });
});

// FE-T008·FE-T014: 동시 401 N건 → 상태 전이·알림 1회, StrictMode에서도 브리지는 하나
describe('세션 상실', () => {
  async function authenticated(strict: boolean) {
    server.use(http.get(ME, () => HttpResponse.json({ memberId: 'm-1' })));
    const client = createApiClient();
    const onSessionLost = vi.fn();
    const result = renderAuth({ adapter: createProvisionalAuthAdapter(client), client, onSessionLost, strict });
    await waitFor(() => expect(status()).toBe('authenticated'));
    return { ...result, onSessionLost };
  }

  it.each([false, true])('동시 401 5건이어도 전이·알림은 1회(StrictMode=%s)', async (strict) => {
    const { client, onSessionLost, log } = await authenticated(strict);
    server.use(
      http.get('*/api/v1/private', async () => {
        await delay(10);
        return HttpResponse.json({ code: 'AUTH_UNAUTHENTICATED' }, { status: 401, headers: PROBLEM });
      }),
    );
    const results = await act(() =>
      Promise.allSettled(Array.from({ length: 5 }, () => client.request({ method: 'get', path: '/v1/private', authScope: 'session' }))),
    );
    expect(results.every((r) => r.status === 'rejected')).toBe(true);
    await waitFor(() => expect(status()).toBe('unauthenticated'));
    expect(onSessionLost).toHaveBeenCalledTimes(1);
    // 초기화 중 상태(요청 ID 0→1)는 여러 개일 수 있다. 인증 뒤의 전이가 정확히 1회인지 본다.
    const afterAuth = log.slice(log.findIndex((s) => s.status === 'authenticated'));
    expect(afterAuth).toEqual([
      expect.objectContaining({ status: 'authenticated', epoch: 1 }),
      { status: 'unauthenticated', epoch: 2, reason: 'sessionLost' },
    ]);
  });

  it('세션 상실은 이전 세대 session 요청만 취소하고 공개 요청은 계속한다(FE-T011)', async () => {
    const { client } = await authenticated(false);
    server.use(
      http.get('*/api/v1/slow', async () => {
        await delay(100);
        return HttpResponse.json({ ok: true });
      }),
      http.get('*/api/v1/private', () =>
        HttpResponse.json({ code: 'AUTH_UNAUTHENTICATED' }, { status: 401, headers: PROBLEM }),
      ),
    );
    const slowSession = client.request({ method: 'get', path: '/v1/slow', authScope: 'session' }).catch((e: unknown) => e);
    const slowPublic = client.request({ method: 'get', path: '/v1/slow', authScope: 'none' });
    await act(async () => {
      await client.request({ method: 'get', path: '/v1/private', authScope: 'session' }).catch(() => {});
    });
    const sessionResult = await slowSession;
    expect(sessionResult).toBeInstanceOf(ApiError);
    expect((sessionResult as ApiError).failure).toEqual({ kind: 'canceled' });
    await expect(slowPublic).resolves.toEqual({ ok: true });
  });

  it('언마운트하면 브리지를 해제한다', async () => {
    const { client, unmount } = await authenticated(false);
    unmount();
    await expect(client.request({ method: 'get', path: '/v1/private', authScope: 'session' })).rejects.toThrow(
      'AuthBridge 연결이 필요',
    );
  });
});

// FE-T010·FE-T009: 재시도 중복·초기화 뒤 재초기화로 요청 ID·세대가 어긋나지 않는다(Bot 리뷰 반영)
describe('재시도·재초기화', () => {
  it('Deadline으로 끝난 뒤에도 재시도 한 번이 허용되고 결과를 반영한다', async () => {
    let calls = 0;
    const adapter: AuthAdapter<unknown> = {
      initialize: () => {
        calls += 1;
        return calls === 1 ? new Promise(() => {}) : Promise.resolve({ authenticated: false });
      },
    };
    renderAuth({ adapter, deadlineMs: 30 });
    await waitFor(() => expect(errorKind()).toBe('deadline'));
    act(() => screen.getByRole('button', { name: 'retry' }).click());
    await waitFor(() => expect(status()).toBe('unauthenticated'));
    expect(calls).toBe(2);
  });

  it('오류 상태에서 retry를 연달아 두 번 불러도 한 번만 재시도하고 결과를 반영한다(무한 로딩 없음)', async () => {
    let calls = 0;
    const adapter: AuthAdapter<unknown> = {
      initialize: async () => {
        calls += 1;
        if (calls === 1) throw new ApiError({ kind: 'http', status: 503, problem: { code: 'AUTH_SESSION_STORE_UNAVAILABLE', fieldErrors: [] } });
        await delay(10);
        return { authenticated: true, principal: { id: 'm' } };
      },
    };
    const holder: { retry?: () => void } = {};
    function Capture() {
      const { retry } = useAuth();
      useEffect(() => {
        holder.retry = retry;
      });
      return null;
    }
    render(
      <AuthProvider adapter={adapter} client={createApiClient()} deadlineMs={1_000}>
        <Probe log={[]} />
        <Capture />
      </AuthProvider>,
    );
    await waitFor(() => expect(errorKind()).toBe('unavailable'));
    const retryFromSameRender = holder.retry;
    act(() => {
      retryFromSameRender?.();
      retryFromSameRender?.();
    });
    await waitFor(() => expect(status()).toBe('authenticated'));
    expect(calls).toBe(2);
  });

  it('초기화가 끝난 뒤 adapter가 바뀌어도 다시 초기화하지 않고, 세대가 맞아 세션 상실이 반영된다', async () => {
    server.use(http.get(ME, () => HttpResponse.json({ memberId: 'm-1' })));
    const client = createApiClient();
    const first = createProvisionalAuthAdapter(client);
    const second: AuthAdapter<unknown> = { initialize: vi.fn(() => Promise.resolve({ authenticated: true, principal: { id: 'other' } })) };
    const { rerender } = render(
      <AuthProvider adapter={first} client={client}>
        <Probe log={[]} />
      </AuthProvider>,
    );
    await waitFor(() => expect(status()).toBe('authenticated'));
    rerender(
      <AuthProvider adapter={second} client={client}>
        <Probe log={[]} />
      </AuthProvider>,
    );
    await act(() => delay(10));
    expect(second.initialize).not.toHaveBeenCalled();

    server.use(http.get('*/api/v1/private', () => HttpResponse.json({ code: 'AUTH_UNAUTHENTICATED' }, { status: 401, headers: PROBLEM })));
    await act(async () => {
      await client.request({ method: 'get', path: '/v1/private', authScope: 'session' }).catch(() => {});
    });
    await waitFor(() => expect(status()).toBe('unauthenticated'));
  });

  it('초기화 중에 adapter가 바뀌면 새 adapter로 초기화한다', async () => {
    const pending: AuthAdapter<unknown> = { initialize: () => new Promise(() => {}) };
    const next: AuthAdapter<unknown> = { initialize: () => Promise.resolve({ authenticated: false }) };
    const client = createApiClient();
    const { rerender } = render(
      <AuthProvider adapter={pending} client={client}>
        <Probe log={[]} />
      </AuthProvider>,
    );
    expect(status()).toBe('initializing');
    rerender(
      <AuthProvider adapter={next} client={client}>
        <Probe log={[]} />
      </AuthProvider>,
    );
    await waitFor(() => expect(status()).toBe('unauthenticated'));
  });
});

describe('useAuth', () => {
  it('Provider 밖에서 쓰면 오류', () => {
    const Bad = () => {
      useAuth();
      return null;
    };
    vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Bad />)).toThrow('AuthProvider');
  });
});
