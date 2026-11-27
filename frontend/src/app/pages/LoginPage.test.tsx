import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, type JsonBodyType } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from '../../features/auth/AuthProvider';
import { unavailableAuthAdapter } from '../../features/auth/unavailableAuthAdapter';
import { useAuth } from '../../features/auth/useAuth';
import { GOOGLE_LOGIN_ERROR_TEXT } from '../../features/login/googleLoginError';
import { OTP_TEXT } from '../../features/otp/otpText';
import { createApiClient } from '../../shared/api/apiClient';
import { server } from '../../test/msw/server';
import { appRoutes } from '../router';
import { LOGIN_PAGE_TEXT } from './loginPageText';

const PROBLEM_JSON = { 'Content-Type': 'application/problem+json' };
const OTP_EXPIRES = '2099-01-01T00:05:00Z';
const SUCCESS = { memberId: 7, role: 'USER', accessTokenExpiresAt: '2099-01-01T00:15:00Z' };

function AuthProbe() {
  const { state } = useAuth();
  const principal = state.status === 'authenticated' ? (state.principal as { memberId: number }) : null;
  return <p data-testid="auth">{principal === null ? state.status : `authenticated:${principal.memberId}`}</p>;
}

function renderAt(initialEntries: Parameters<typeof createMemoryRouter>[1] extends infer O ? O extends { initialEntries?: infer E } ? E : never : never) {
  document.cookie = 'XSRF-TOKEN=t1; path=/';
  const router = createMemoryRouter(appRoutes, { initialEntries });
  render(
    <AuthProvider adapter={unavailableAuthAdapter} client={createApiClient()}>
      <RouterProvider router={router} />
      <AuthProbe />
    </AuthProvider>,
  );
  return router;
}

function loginSucceeds() {
  server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: OTP_EXPIRES })));
}

function verifyResponds(status: number, body: JsonBodyType) {
  server.use(
    http.post('*/api/v1/auth/otp/verify', () =>
      HttpResponse.json(body, { status, headers: status === 200 ? {} : PROBLEM_JSON }),
    ),
  );
}

const field = (name: string) => screen.getByLabelText(name, { selector: 'input' });

async function logIn(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('아이디', { selector: 'input' }), 'tester01');
  await user.type(field('비밀번호'), 'pw');
  await user.click(screen.getByRole('button', { name: '로그인' }));
  await screen.findByRole('heading', { name: '인증 번호 확인' });
}

async function enterOtp(user: ReturnType<typeof userEvent.setup>, code = '0420') {
  await user.type(field('인증 번호'), code);
  await user.click(screen.getByRole('button', { name: '확인' }));
}

// FE-T039: 경로·링크
describe('로그인 화면', () => {
  it('로그인 폼, Google 시작 링크(문서 이동), 가입 링크가 있다', async () => {
    renderAt(['/login']);
    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Google로 로그인' })).toHaveAttribute('href', '/api/v1/auth/oauth2/authorization/google');
    expect(screen.getByRole('link', { name: '회원가입' })).toHaveAttribute('href', '/signup');
    expect(screen.queryByText('로그인 화면은 준비 중입니다.')).not.toBeInTheDocument();
  });
});

// FE-T038: Google 오류 안내(FE-D038)
describe('Google 오류 안내', () => {
  it('error 코드의 고정 문구를 보이고 쿼리에서 error만 지운다(from 유지)', async () => {
    const router = renderAt(['/login?error=oauth_email_unverified&from=%2Fapp']);
    expect(await screen.findByText(GOOGLE_LOGIN_ERROR_TEXT.oauth_email_unverified)).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.search).toBe('?from=%2Fapp'));
    expect(screen.getByText(GOOGLE_LOGIN_ERROR_TEXT.oauth_email_unverified)).toBeInTheDocument();
  });

  it('알 수 없는 값은 일반 문구이고 받은 값을 화면에 그리지 않는다', async () => {
    const value = '<b>pwned</b>';
    renderAt([`/login?error=${encodeURIComponent(value)}`]);
    expect(await screen.findByText(GOOGLE_LOGIN_ERROR_TEXT.unknown)).toBeInTheDocument();
    expect(document.body.innerHTML).not.toContain('pwned');
  });
});

// FE-T034·FE-T036: 로그인 → OTP → 인증 전환·이동(FE-D034~FE-D036)
describe('일반 로그인 흐름', () => {
  it('가드가 보낸 from으로 돌아가고 인증 상태가 된다', async () => {
    loginSucceeds();
    verifyResponds(200, SUCCESS);
    const user = userEvent.setup();
    const router = renderAt(['/app']);
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    await logIn(user);
    expect(router.state.location.pathname).toBe('/login/otp');
    expect(screen.getByText(/남은 시간/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /초 뒤 다시 받기/ })).toBeDisabled();
    await enterOtp(user);
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'));
    await waitFor(() => expect(screen.getByTestId('auth')).toHaveTextContent('authenticated:7'));
  });

  it('안전하지 않은 from은 따르지 않고 FE-D012대로 /로 간다', async () => {
    loginSucceeds();
    verifyResponds(200, SUCCESS);
    const user = userEvent.setup();
    const router = renderAt([`/login?from=${encodeURIComponent('//evil.example/x')}`]);
    await logIn(user);
    await enterOtp(user);
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });

  it('OTP 성공은 replace 이동이라 뒤로 가기로 OTP 화면에 돌아가지 않는다', async () => {
    loginSucceeds();
    verifyResponds(200, SUCCESS);
    const user = userEvent.setup();
    const router = renderAt(['/', '/login']);
    await logIn(user);
    await enterOtp(user);
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'));
    await router.navigate(-1);
    await waitFor(() => expect(router.state.location.pathname).not.toBe('/login/otp'));
  });

  it.each<[string, number, string, string]>([
    ['Challenge 없음', 401, 'AUTH_OTP_CHALLENGE_INVALID', LOGIN_PAGE_TEXT.challengeInvalid],
    ['OTP 단계 로그인 실패', 401, 'AUTH_LOGIN_FAILED', LOGIN_PAGE_TEXT.loginFailed],
    ['계정 사용 중지', 403, 'MEMBER_ACCOUNT_DISABLED', LOGIN_PAGE_TEXT.disabled],
  ])('%s면 로그인 화면으로 replace 이동해 사유를 안내한다', async (_name, status, code, text) => {
    loginSucceeds();
    verifyResponds(status, { code });
    const user = userEvent.setup();
    const router = renderAt(['/login?from=%2Fapp']);
    await logIn(user);
    await enterOtp(user);
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.search).toBe('?from=%2Fapp');
    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    // FE-D042 (2): 한 번 보인 뒤 history state에서 지워 새로고침하면 다시 보이지 않는다(from 쿼리는 유지)
    await waitFor(() => expect(router.state.location.state).toBeNull());
    expect(router.state.location.search).toBe('?from=%2Fapp');
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it('재발송하면 화면 기록(history state)의 만료 시각을 새 값으로 바꾼다', async () => {
    loginSucceeds();
    server.use(
      http.post('*/api/v1/auth/otp/resend', () =>
        HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: '2099-01-01T00:09:00Z' }),
      ),
    );
    const user = userEvent.setup();
    const router = renderAt(['/']);
    await router.navigate('/login/otp');
    await user.click(await screen.findByRole('button', { name: OTP_TEXT.resend }));
    await screen.findByText(OTP_TEXT.resent);
    expect(router.state.location.state).toMatchObject({ otpExpiresAt: Date.parse('2099-01-01T00:09:00Z') });
  });
});

describe('Google 진입(상태 없이 OTP 화면)', () => {
  it('남은 시간 없이 바로 재발송할 수 있고, 성공하면 /app으로 간다', async () => {
    verifyResponds(200, SUCCESS);
    const user = userEvent.setup();
    const router = renderAt(['/login/otp']);
    expect(await screen.findByRole('heading', { name: '인증 번호 확인' })).toBeInTheDocument();
    expect(screen.queryByText(/남은 시간/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: OTP_TEXT.resend })).toBeEnabled();
    await enterOtp(user);
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'));
  });

  it('history state의 만료 시각이 숫자가 아니면 쓰지 않는다', async () => {
    verifyResponds(200, SUCCESS);
    const user = userEvent.setup();
    const router = renderAt([{ pathname: '/login/otp', state: { otpExpiresAt: 'x', resendAvailableAt: 'y' } }]);
    await screen.findByRole('heading', { name: '인증 번호 확인' });
    expect(screen.queryByText(/남은 시간/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: OTP_TEXT.resend })).toBeEnabled();
    await enterOtp(user);
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'));
  });

  it('history state의 from도 다시 검사한다(조작된 값은 /)', async () => {
    verifyResponds(200, SUCCESS);
    const user = userEvent.setup();
    const router = renderAt([{ pathname: '/login/otp', state: { from: 'https://evil.example' } }]);
    await screen.findByRole('heading', { name: '인증 번호 확인' });
    await enterOtp(user);
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });
});
