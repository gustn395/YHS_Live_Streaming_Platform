import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../features/auth/authContext';
import { OTP_TEXT } from '../../features/otp/otpText';
import { server } from '../../test/msw/server';
import { appRoutes } from '../router';

const SUCCESS = { memberId: 7, role: 'USER', accessTokenExpiresAt: '2099-01-01T00:15:00Z' };

// FE-T036(FE-D042 (1)): 로그인 성공 전환이 반영되지 않으면 보호 화면으로 가지 않는다
describe('OTP 화면 — 로그인 성공 전환 실패', () => {
  it('completeLogin이 false면 이동하지 않고 안내와 다시 로그인 링크를 보인다', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    server.use(http.post('*/api/v1/auth/otp/verify', () => HttpResponse.json(SUCCESS)));
    const completeLogin = vi.fn(() => false);
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/login/otp'] });
    render(
      <AuthContext value={{ state: { status: 'unauthenticated', epoch: 0, reason: 'initial' }, retry: vi.fn(), completeLogin }}>
        <RouterProvider router={router} />
      </AuthContext>,
    );
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('인증 번호', { selector: 'input' }), '0420');
    await user.click(screen.getByRole('button', { name: '확인' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(OTP_TEXT.loginNotApplied);
    expect(completeLogin).toHaveBeenCalledTimes(1);
    expect(router.state.location.pathname).toBe('/login/otp');
    expect(screen.getByRole('link', { name: OTP_TEXT.restartLink })).toHaveAttribute('href', '/login');
  });

  it('OTP 화면에는 처음부터 다시 로그인하는 링크가 늘 있다', async () => {
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/login/otp'] });
    render(
      <AuthContext
        value={{ state: { status: 'unauthenticated', epoch: 0, reason: 'initial' }, retry: vi.fn(), completeLogin: vi.fn(() => true) }}
      >
        <RouterProvider router={router} />
      </AuthContext>,
    );
    await waitFor(() => expect(screen.getByRole('link', { name: OTP_TEXT.restartLink })).toBeInTheDocument());
  });

  it('다시 로그인 링크는 원래 가려던 경로(from)를 유지한다', async () => {
    const router = createMemoryRouter(appRoutes, { initialEntries: [{ pathname: '/login/otp', state: { from: '/app' } }] });
    render(
      <AuthContext
        value={{ state: { status: 'unauthenticated', epoch: 0, reason: 'initial' }, retry: vi.fn(), completeLogin: vi.fn(() => true) }}
      >
        <RouterProvider router={router} />
      </AuthContext>,
    );
    expect(await screen.findByRole('link', { name: OTP_TEXT.restartLink })).toHaveAttribute('href', '/login?from=%2Fapp');
  });
});
