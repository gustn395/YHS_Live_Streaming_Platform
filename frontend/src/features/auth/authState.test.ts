import { describe, expect, it } from 'vitest';
import { authReducer, initialAuthState, type AuthEvent, type AuthState } from './authState';

type P = { id: string };
const reduce = (state: AuthState<P>, ...events: AuthEvent<P>[]) => events.reduce(authReducer<P>, state);

const started = (requestId: number, epoch = 0): AuthEvent<P> => ({ type: 'INIT_STARTED', requestId, epoch });
const succeeded = (requestId: number, epoch = 0): AuthEvent<P> => ({
  type: 'INIT_SUCCEEDED',
  requestId,
  epoch,
  result: { authenticated: true, principal: { id: 'm1' } },
});
const unauthenticated = (requestId: number, epoch = 0): AuthEvent<P> => ({
  type: 'INIT_SUCCEEDED',
  requestId,
  epoch,
  result: { authenticated: false },
});
const failed = (requestId: number, epoch = 0): AuthEvent<P> => ({
  type: 'INIT_FAILED',
  requestId,
  epoch,
  error: { kind: 'unavailable', status: 503, code: 'AUTH_SESSION_STORE_UNAVAILABLE' },
});

// FE-T032: 로그인(OTP 확인) 성공 전환(FE-D034)
describe('LOGIN_SUCCEEDED', () => {
  const login = (epoch: number, id = 'm2'): AuthEvent<P> => ({ type: 'LOGIN_SUCCEEDED', epoch, principal: { id } });
  const unauth = reduce(initialAuthState, started(1), unauthenticated(1));

  it('unauthenticated에서 authenticated가 되고 세대를 1 올린다', () => {
    expect(reduce(unauth, login(0))).toEqual({ status: 'authenticated', epoch: 1, principal: { id: 'm2' } });
  });

  it('authenticated에서 다시 로그인하면 주체를 바꾸고 세대를 1 올린다', () => {
    const authed = reduce(initialAuthState, started(1), succeeded(1));
    expect(reduce(authed, login(1, 'm3'))).toEqual({ status: 'authenticated', epoch: 2, principal: { id: 'm3' } });
  });

  it('세션 상실 뒤(unauthenticated·sessionLost)에도 로그인할 수 있다', () => {
    const lost = reduce(initialAuthState, started(1), succeeded(1), { type: 'SESSION_LOST', epoch: 1 });
    expect(reduce(lost, login(2))).toEqual({ status: 'authenticated', epoch: 3, principal: { id: 'm2' } });
  });

  it('initializing에서는 반영하지 않는다(같은 참조)', () => {
    const initializing = reduce(initialAuthState, started(1));
    expect(reduce(initializing, login(0))).toBe(initializing);
  });

  it('세대가 다르면 반영하지 않는다(같은 참조)', () => {
    expect(reduce(unauth, login(1))).toBe(unauth);
  });
});

describe('초기 상태', () => {
  it('initializing, 세대 0, 오류 없음', () => {
    expect(initialAuthState).toEqual({ status: 'initializing', epoch: 0, initRequestId: 0, initError: null });
  });
});

// FE-T010(Reducer 부분): 초기화 결과 분류는 Provider가 하고, Reducer는 받은 결과를 전이한다(FE-D009)
describe('초기화 전이', () => {
  it('인증 성공 → authenticated, 세대 +1', () => {
    expect(reduce(initialAuthState, started(1), succeeded(1))).toEqual({
      status: 'authenticated',
      epoch: 1,
      principal: { id: 'm1' },
    });
  });

  it('미인증 → unauthenticated(initial), 세대 그대로', () => {
    expect(reduce(initialAuthState, started(1), unauthenticated(1))).toEqual({
      status: 'unauthenticated',
      epoch: 0,
      reason: 'initial',
    });
  });

  it('실패 → initializing에 initError, 세대 그대로', () => {
    expect(reduce(initialAuthState, started(1), failed(1))).toEqual({
      status: 'initializing',
      epoch: 0,
      initRequestId: 1,
      initError: { kind: 'unavailable', status: 503, code: 'AUTH_SESSION_STORE_UNAVAILABLE' },
    });
  });

  it('재시도하면 initError를 지우고 새 요청 ID만 받는다', () => {
    const errored = reduce(initialAuthState, started(1), failed(1));
    const retried = reduce(errored, { type: 'INIT_RETRY', requestId: 2, epoch: 0 });
    expect(retried).toEqual({ status: 'initializing', epoch: 0, initRequestId: 2, initError: null });
  });

  it('오류가 없으면 재시도 이벤트를 무시한다', () => {
    const s = reduce(initialAuthState, started(1));
    expect(authReducer(s, { type: 'INIT_RETRY', requestId: 2, epoch: 0 })).toBe(s);
  });
});

// FE-T009(Reducer 부분): 요청 ID·상태·세대가 모두 맞는 초기화 결과만 반영한다(FE-D011)
describe('오래된 초기화 결과 무시', () => {
  it('이전 요청 ID의 결과는 무시한다(같은 참조 반환)', () => {
    const s = reduce(initialAuthState, started(1), failed(1), { type: 'INIT_RETRY', requestId: 2, epoch: 0 });
    expect(authReducer(s, succeeded(1))).toBe(s);
    expect(authReducer(s, failed(1))).toBe(s);
  });

  it('initializing이 아니면 초기화 결과를 무시한다', () => {
    const s = reduce(initialAuthState, started(1), unauthenticated(1));
    expect(authReducer(s, succeeded(1))).toBe(s);
  });

  it('시작 세대가 다르면 무시한다', () => {
    const s = reduce(initialAuthState, started(1));
    expect(authReducer(s, succeeded(1, 5))).toBe(s);
  });

  it('초기화가 끝난 뒤의 시작 이벤트는 무시한다', () => {
    const s = reduce(initialAuthState, started(1), succeeded(1));
    expect(authReducer(s, started(2, 1))).toBe(s);
  });
});

// FE-T008(Reducer 부분): 세션 상실은 같은 세대에 한 번만 전이한다
describe('세션 상실', () => {
  const authed = reduce(initialAuthState, started(1), succeeded(1));

  it('authenticated + 같은 세대 → unauthenticated(sessionLost), 세대 +1', () => {
    expect(authReducer(authed, { type: 'SESSION_LOST', epoch: 1 })).toEqual({
      status: 'unauthenticated',
      epoch: 2,
      reason: 'sessionLost',
    });
  });

  it('같은 세대 상실이 여러 번 와도 한 번만 전이한다', () => {
    const once = authReducer(authed, { type: 'SESSION_LOST', epoch: 1 });
    expect(authReducer(once, { type: 'SESSION_LOST', epoch: 1 })).toBe(once);
  });

  it('다른 세대의 상실은 무시한다', () => {
    expect(authReducer(authed, { type: 'SESSION_LOST', epoch: 0 })).toBe(authed);
  });

  it('미인증·초기화 중에는 무시한다', () => {
    expect(authReducer(initialAuthState, { type: 'SESSION_LOST', epoch: 0 })).toBe(initialAuthState);
  });
});

// FE-T017: Reducer는 순수 함수다
describe('순수성', () => {
  const events: AuthEvent<P>[] = [
    started(1),
    failed(1),
    { type: 'INIT_RETRY', requestId: 2, epoch: 0 },
    succeeded(2),
    { type: 'SESSION_LOST', epoch: 1 },
  ];

  it('같은 입력에 같은 출력, 입력 상태를 바꾸지 않는다', () => {
    const frozen = Object.freeze({ ...initialAuthState });
    const first = reduce(frozen, ...events);
    const second = reduce(frozen, ...events);
    expect(first).toEqual(second);
    expect(frozen).toEqual(initialAuthState);
  });

  it('외부 부수 효과(타이머·네트워크·랜덤·시간)를 쓰지 않는다', () => {
    const spies = [
      globalThis.setTimeout,
      globalThis.fetch,
      Math.random,
      Date.now,
    ];
    const originals = { setTimeout: globalThis.setTimeout, fetch: globalThis.fetch, random: Math.random, now: Date.now };
    let calls = 0;
    const trap = () => {
      calls += 1;
      throw new Error('부수 효과');
    };
    globalThis.setTimeout = trap as unknown as typeof setTimeout;
    globalThis.fetch = trap as unknown as typeof fetch;
    Math.random = trap;
    Date.now = trap;
    try {
      reduce(initialAuthState, ...events);
    } finally {
      globalThis.setTimeout = originals.setTimeout;
      globalThis.fetch = originals.fetch;
      Math.random = originals.random;
      Date.now = originals.now;
    }
    expect(calls).toBe(0);
    expect(spies).toHaveLength(4);
  });
});
