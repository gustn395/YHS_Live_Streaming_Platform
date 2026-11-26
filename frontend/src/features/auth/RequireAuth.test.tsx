import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext } from './authContext';
import type { AuthState } from './authState';
import { RequireAuth } from './RequireAuth';

function renderGuard(state: AuthState<unknown>, retry = vi.fn(), initialEntry = '/app/inner?x=1#h') {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <p>login page</p> },
      { element: <RequireAuth />, children: [{ path: '/app/inner', element: <p>protected content</p> }] },
    ],
    { initialEntries: [initialEntry] },
  );
  render(
    <AuthContext value={{ state, retry }}>
      <RouterProvider router={router} />
    </AuthContext>,
  );
  return { router, retry };
}

// FE-T012: 가드는 화면 접근 제어일 뿐 보안 경계가 아니다(FE-D012)
describe('RequireAuth', () => {
  it('초기화 중(오류 없음) → 로딩 표시, 이동 없음', () => {
    const { router } = renderGuard({ status: 'initializing', epoch: 0, initRequestId: 1, initError: null });
    expect(screen.getByRole('status')).toHaveTextContent('확인하는 중');
    expect(router.state.location.pathname).toBe('/app/inner');
  });

  it('초기화 오류 → 오류와 재시도, 로그인으로 보내지 않음', async () => {
    const { router, retry } = renderGuard({
      status: 'initializing',
      epoch: 0,
      initRequestId: 1,
      initError: { kind: 'unavailable', status: 503, code: 'AUTH_SESSION_STORE_UNAVAILABLE' },
    });
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/app/inner');
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('미인증 → /login으로 replace 이동, from에 원래 경로 보존', () => {
    const { router } = renderGuard({ status: 'unauthenticated', epoch: 0, reason: 'initial' });
    expect(router.state.location.pathname).toBe('/login');
    expect(new URLSearchParams(router.state.location.search).get('from')).toBe('/app/inner?x=1#h');
    expect(router.state.historyAction).toBe('REPLACE');
    expect(screen.getByText('login page')).toBeInTheDocument();
  });

  it('인증 → 보호 화면 표시', () => {
    renderGuard({ status: 'authenticated', epoch: 1, principal: {} });
    expect(screen.getByText('protected content')).toBeInTheDocument();
  });

  it('인증 → 세션 상실 시 /login으로 한 번만 이동한다(FE-T008)', async () => {
    const router = createMemoryRouter(
      [
        { path: '/login', element: <p>login page</p> },
        { element: <RequireAuth />, children: [{ path: '/app', element: <p>protected content</p> }] },
      ],
      { initialEntries: ['/app'] },
    );
    const navigations: string[] = [];
    router.subscribe((s) => navigations.push(s.location.pathname));
    const authed: AuthState<unknown> = { status: 'authenticated', epoch: 1, principal: {} };
    const { rerender } = render(
      <AuthContext value={{ state: authed, retry: vi.fn() }}>
        <RouterProvider router={router} />
      </AuthContext>,
    );
    const lost: AuthState<unknown> = { status: 'unauthenticated', epoch: 2, reason: 'sessionLost' };
    await act(async () => {
      rerender(
        <AuthContext value={{ state: lost, retry: vi.fn() }}>
          <RouterProvider router={router} />
        </AuthContext>,
      );
    });
    expect(navigations.filter((p) => p === '/login')).toHaveLength(1);
  });
});
