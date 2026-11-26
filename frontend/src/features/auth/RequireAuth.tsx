import { Navigate, Outlet, useLocation } from 'react-router';
import type { InitError } from './authState';
import { useAuth } from './useAuth';

function initErrorMessage(error: InitError): string {
  return error.kind === 'unavailable'
    ? '일시적으로 로그인 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.'
    : '로그인 상태를 확인하지 못했습니다. 다시 시도해 주세요.';
}

/**
 * 인증 필요 화면 앞의 화면 접근 제어다. 보안 경계가 아니며 실제 인증·인가는 Backend가 검증한다(FE-D012).
 */
export function RequireAuth() {
  const { state, retry } = useAuth();
  const location = useLocation();

  switch (state.status) {
    case 'initializing':
      if (state.initError === null) return <p role="status">로그인 상태를 확인하는 중입니다.</p>;
      return (
        <div role="alert">
          <p>{initErrorMessage(state.initError)}</p>
          <button type="button" onClick={retry}>
            다시 시도
          </button>
        </div>
      );
    case 'unauthenticated': {
      const from = location.pathname + location.search + location.hash;
      return <Navigate to={`/login?${new URLSearchParams({ from }).toString()}`} replace />;
    }
    case 'authenticated':
      return <Outlet />;
  }
}
