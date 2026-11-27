/**
 * 초기화가 "미인증"이 아닌 이유로 끝나지 못한 것(FE-D009). 미인증으로 보지 않고 재시도를 안내한다.
 * - unavailable: 503 AUTH_SESSION_STORE_UNAVAILABLE(G-D007)
 * - server: 그 밖의 HTTP 오류(ProblemDetail이 아닌 401 포함)
 * - deadline: 초기화 전체 상한(AuthProvider의 AUTH_INIT_DEADLINE_MS) 초과
 */
export type InitError =
  | { kind: 'unavailable'; status: number; code?: string }
  | { kind: 'server'; status: number; code?: string }
  | { kind: 'network' }
  | { kind: 'timeout' }
  | { kind: 'invalid-response'; status: number }
  | { kind: 'deadline' }
  | { kind: 'unexpected' };

/** 회원 정보 P의 모양은 어댑터가 정한다. Backend 계약 전이므로 여기서 필드를 정하지 않는다. */
export type AuthState<P> =
  | { status: 'initializing'; epoch: number; initRequestId: number; initError: InitError | null }
  | { status: 'unauthenticated'; epoch: number; reason: 'initial' | 'sessionLost' }
  | { status: 'authenticated'; epoch: number; principal: P };

export type InitResult<P> = { authenticated: true; principal: P } | { authenticated: false };

/** 초기화 이벤트의 epoch는 그 초기화를 시작한 세대다. */
export type AuthEvent<P> =
  | { type: 'INIT_STARTED'; requestId: number; epoch: number }
  | { type: 'INIT_RETRY'; requestId: number; epoch: number }
  | { type: 'INIT_SUCCEEDED'; requestId: number; epoch: number; result: InitResult<P> }
  | { type: 'INIT_FAILED'; requestId: number; epoch: number; error: InitError }
  | { type: 'SESSION_LOST'; epoch: number }
  /** 로그인(OTP 확인) 성공(FE-D034). epoch는 성공을 반영하기 직전 세대다. */
  | { type: 'LOGIN_SUCCEEDED'; epoch: number; principal: P };

export const initialAuthState: AuthState<never> = {
  status: 'initializing',
  epoch: 0,
  initRequestId: 0,
  initError: null,
};

/**
 * 순수 함수다(FE-D009). 반영하지 않는 이벤트는 같은 참조를 돌려준다.
 * 세대는 새 인증 성공(초기화·로그인)과 세션 상실에서만 올린다(FE-D011, FE-D034).
 */
export function authReducer<P>(state: AuthState<P>, event: AuthEvent<P>): AuthState<P> {
  switch (event.type) {
    case 'INIT_STARTED':
      if (state.status !== 'initializing' || state.epoch !== event.epoch) return state;
      return { status: 'initializing', epoch: state.epoch, initRequestId: event.requestId, initError: null };

    case 'INIT_RETRY':
      if (state.status !== 'initializing' || state.initError === null || state.epoch !== event.epoch) return state;
      return { status: 'initializing', epoch: state.epoch, initRequestId: event.requestId, initError: null };

    case 'INIT_SUCCEEDED':
      if (!isCurrentInit(state, event)) return state;
      return event.result.authenticated
        ? { status: 'authenticated', epoch: state.epoch + 1, principal: event.result.principal }
        : { status: 'unauthenticated', epoch: state.epoch, reason: 'initial' };

    case 'INIT_FAILED':
      if (!isCurrentInit(state, event)) return state;
      return { status: 'initializing', epoch: state.epoch, initRequestId: state.initRequestId, initError: event.error };

    case 'SESSION_LOST':
      if (state.status !== 'authenticated' || state.epoch !== event.epoch) return state;
      return { status: 'unauthenticated', epoch: state.epoch + 1, reason: 'sessionLost' };

    case 'LOGIN_SUCCEEDED':
      // 초기화가 끝난 상태에서만 연다. 같은 탭 재로그인은 주체를 바꾼다(FE-D034, FE-D016은 다시 초기화하지 않는다).
      if (state.status === 'initializing' || state.epoch !== event.epoch) return state;
      return { status: 'authenticated', epoch: state.epoch + 1, principal: event.principal };
  }
}

function isCurrentInit<P>(
  state: AuthState<P>,
  event: { requestId: number; epoch: number },
): state is Extract<AuthState<P>, { status: 'initializing' }> {
  return state.status === 'initializing' && state.initRequestId === event.requestId && state.epoch === event.epoch;
}
