import { useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState, type ReactNode } from 'react';
import type { ApiClient } from '../../shared/api/apiClient';
import type { AuthAdapter } from './authAdapter';
import { AuthContext, type AuthContextValue } from './authContext';
import { authReducer, initialAuthState, type AuthState } from './authState';
import { classifyInitFailure } from './classifyInitFailure';
import { createSessionScope } from './sessionScope';

/** 인증 초기화 전체 상한(FE-01 구현 계획 확정, 2026-11-26). HTTP Timeout(10초)과 별개로 무한 로딩을 막는다. */
export const AUTH_INIT_DEADLINE_MS = 15_000;

interface AuthProviderProps<P> {
  adapter: AuthAdapter<P>;
  client: ApiClient;
  deadlineMs?: number;
  /** 세션 상실을 한 번만 알린다(동시 401 중복 방지). */
  onSessionLost?: () => void;
  children: ReactNode;
}

interface ActiveInit {
  id: number;
  controller: AbortController;
  timer: ReturnType<typeof setTimeout>;
}

/**
 * 인증 상태(Context + useReducer, FE-D009)와 API Client 브리지를 연결한다.
 * Reducer는 순수하고, 취소·타이머·네트워크 같은 부수 효과는 여기서만 다룬다.
 */
export function AuthProvider<P>({
  adapter,
  client,
  deadlineMs = AUTH_INIT_DEADLINE_MS,
  onSessionLost,
  children,
}: AuthProviderProps<P>) {
  const [state, dispatch] = useReducer(authReducer<P>, initialAuthState as AuthState<P>);

  const [scope] = useState(createSessionScope);

  // 세션 상실 구독을 브리지 연결보다 먼저 한다(effect는 선언 순서대로 실행된다).
  useEffect(
    () =>
      scope.subscribeSessionLost((epoch) => {
        dispatch({ type: 'SESSION_LOST', epoch });
        onSessionLost?.();
      }),
    [scope, onSessionLost],
  );

  // 브리지는 하나만 붙는다. StrictMode의 effect 재실행도 해제 → 연결로 짝을 맞춘다(FE-T014).
  useEffect(() => client.attachAuthBridge(scope), [client, scope]);

  const seqRef = useRef(0);
  const activeRef = useRef<ActiveInit | null>(null);
  // 초기화가 결과(인증·미인증)로 끝났는지. 끝난 뒤에는 다시 초기화하지 않는다(FE-D016, Reducer 세대와 scope 세대 일치 유지).
  const initDoneRef = useRef(false);

  const cancelActive = useCallback(() => {
    const active = activeRef.current;
    if (active === null) return;
    clearTimeout(active.timer);
    active.controller.abort();
    activeRef.current = null;
  }, []);

  // 초기화 설정 토큰. adapter·deadlineMs·scope 중 하나라도 바뀌면 새 토큰이 되고, 커밋 시점(layout 단계)에 현재 토큰으로 기록한다.
  // 이전 토큰으로 시작한 요청은 새 설정이 커밋된 뒤에는 — passive effect가 취소하기 전이라도 — 결과를 반영하지 않는다.
  // layout cleanup에서 취소하지 않는다: Suspense·Activity가 화면을 숨기면 layout cleanup만 실행되어 초기화가 멈출 수 있다.
  const initConfig = useMemo(() => ({ adapter, deadlineMs, scope }), [adapter, deadlineMs, scope]);
  const committedConfigRef = useRef(initConfig);
  useLayoutEffect(() => {
    committedConfigRef.current = initConfig;
  }, [initConfig]);

  const startInit = useCallback(
    (type: 'INIT_STARTED' | 'INIT_RETRY') => {
      const { adapter, deadlineMs, scope } = initConfig;
      // 초기화가 끝난 뒤 adapter 등이 바뀌어 effect가 다시 돌아도 재초기화하지 않는다.
      // Reducer는 initializing이 아니면 결과를 버리지만 scope는 세대를 올려 둘이 어긋나기 때문이다.
      if (initDoneRef.current) return;
      cancelActive();
      seqRef.current += 1;
      const id = seqRef.current;
      const epoch = scope.currentEpoch();
      const controller = new AbortController();
      const isActive = () => activeRef.current?.id === id && committedConfigRef.current === initConfig;
      const finish = () => {
        if (!isActive()) return false;
        clearTimeout(activeRef.current?.timer);
        activeRef.current = null;
        return true;
      };

      const timer = setTimeout(() => {
        if (!finish()) return;
        controller.abort();
        dispatch({ type: 'INIT_FAILED', requestId: id, epoch, error: { kind: 'deadline' } });
      }, deadlineMs);
      activeRef.current = { id, controller, timer };
      dispatch({ type, requestId: id, epoch });

      adapter.initialize({ signal: controller.signal }).then(
        (result) => {
          if (!finish()) return; // 취소·Deadline 뒤 늦게 온 결과는 반영하지 않는다
          if (result.authenticated && !scope.markAuthenticated(epoch)) {
            // 시작 세대가 바뀌었다(현재 구조에서는 도달하지 않는다). 로딩에 남지 않도록 오류로 끝낸다.
            dispatch({ type: 'INIT_FAILED', requestId: id, epoch, error: { kind: 'unexpected' } });
            return;
          }
          initDoneRef.current = true;
          dispatch({ type: 'INIT_SUCCEEDED', requestId: id, epoch, result });
        },
        (error: unknown) => {
          if (!isActive()) return;
          const outcome = classifyInitFailure(error);
          // 우리가 취소하지 않았는데 취소로 끝난 경우: 타이머를 남겨 Deadline이 initError로 바꾼다.
          if (outcome.type === 'ignore') return;
          finish();
          if (outcome.type === 'unauthenticated') initDoneRef.current = true;
          dispatch(
            outcome.type === 'unauthenticated'
              ? { type: 'INIT_SUCCEEDED', requestId: id, epoch, result: { authenticated: false } }
              : { type: 'INIT_FAILED', requestId: id, epoch, error: outcome.error },
          );
        },
      );
    },
    [initConfig, cancelActive],
  );

  useEffect(() => {
    startInit('INIT_STARTED');
    return cancelActive;
  }, [startInit, cancelActive]);

  const retry = useCallback(() => {
    // 진행 중인 재시도가 있으면 동기적으로 막는다. 렌더 전 같은 retry가 두 번 불려도 요청 ID가 Reducer와 어긋나지 않는다.
    if (activeRef.current !== null) return;
    if (state.status === 'initializing' && state.initError !== null) startInit('INIT_RETRY');
  }, [state, startInit]);

  const completeLogin = useCallback(
    (principal: unknown) => {
      // 초기화 결과가 나기 전에는 열지 않는다(Reducer도 initializing에서는 반영하지 않는다).
      if (!initDoneRef.current) return false;
      const epoch = scope.currentEpoch();
      // scope가 먼저 세대를 올려 이전 세대 session 요청을 취소하고, Reducer는 같은 직전 세대로 전이한다.
      if (!scope.markAuthenticated(epoch)) return false;
      dispatch({ type: 'LOGIN_SUCCEEDED', epoch, principal: principal as P });
      return true;
    },
    [scope],
  );

  const value = useMemo<AuthContextValue>(() => ({ state, retry, completeLogin }), [state, retry, completeLogin]);
  return <AuthContext value={value}>{children}</AuthContext>;
}
