import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from '../../features/auth/AuthProvider';
import { unavailableAuthAdapter } from '../../features/auth/unavailableAuthAdapter';
import { useAuth } from '../../features/auth/useAuth';
import { createApiClient } from '../../shared/api/apiClient';
import { server } from '../../test/msw/server';
import { createProvisionalAuthAdapter, PROVISIONAL_ME_PATH } from '../../test/provisionalAuthAdapter';
import { appRoutes } from '../router';

const field = (name: RegExp | string) => screen.getByLabelText(name, { selector: 'input' });

async function signUp(user: ReturnType<typeof userEvent.setup>) {
  await user.type(field('아이디'), 'tester01');
  await user.type(field('이메일'), 'tester@example.com');
  await user.type(field('닉네임'), '테스터');
  await user.type(field(/^비밀번호$/), 'correct horse battery staple');
  await user.type(field('비밀번호 확인'), 'correct horse battery staple');
  await user.click(screen.getByRole('button', { name: '가입하기' }));
}

function AuthProbe() {
  const { state } = useAuth();
  const principal = state.status === 'authenticated' ? (state.principal as { memberId: string }) : null;
  return <p data-testid="auth">{principal === null ? state.status : `authenticated:${principal.memberId}`}</p>;
}

function renderAt(initialEntries: Parameters<typeof createMemoryRouter>[1] extends infer O ? O extends { initialEntries?: infer E } ? E : never : never, initialIndex?: number) {
  const router = createMemoryRouter(appRoutes, { initialEntries, ...(initialIndex === undefined ? {} : { initialIndex }) });
  const client = createApiClient();
  render(
    <AuthProvider adapter={unavailableAuthAdapter} client={client}>
      <RouterProvider router={router} />
    </AuthProvider>,
  );
  return router;
}

function signupSucceeds() {
  document.cookie = 'XSRF-TOKEN=t1; path=/';
  server.use(http.post('*/api/v1/auth/signup', () => HttpResponse.json({ memberId: 98765, accountStatus: 'PENDING' }, { status: 201 })));
}

// FE-T027: 가입 완료 흐름(FE-D026)
describe('가입 완료 흐름', () => {
  it('201이면 완료 화면으로 replace 이동하고 memberId는 보이지 않는다', async () => {
    signupSucceeds();
    const user = userEvent.setup();
    const router = renderAt(['/', '/signup'], 1);
    await signUp(user);
    expect(await screen.findByRole('heading', { name: '가입 신청이 완료됐습니다' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/signup/complete');
    expect(screen.queryByText(/98765/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인하러 가기' })).toHaveAttribute('href', '/login');
  });

  it('뒤로 가기는 가입 폼이 아니라 그 전 화면으로 간다', async () => {
    signupSucceeds();
    const user = userEvent.setup();
    const router = renderAt(['/', '/signup'], 1);
    await signUp(user);
    await screen.findByRole('heading', { name: '가입 신청이 완료됐습니다' });
    await router.navigate(-1);
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });

  it('가입 성공 때 넘긴 state가 있으면 새로고침해도 완료 안내를 보인다', async () => {
    renderAt([{ pathname: '/signup/complete', state: { signupCompleted: true } }]);
    expect(await screen.findByRole('heading', { name: '가입 신청이 완료됐습니다' })).toBeInTheDocument();
  });

  it('state 없이 완료 화면에 직접 오면 가입 화면으로 replace 이동한다', async () => {
    const router = renderAt(['/', '/signup/complete'], 1);
    await waitFor(() => expect(router.state.location.pathname).toBe('/signup'));
    expect(screen.getByRole('heading', { name: '회원가입' })).toBeInTheDocument();
    await router.navigate(-1);
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });
});

// FE-T027: 가입은 인증 상태를 바꾸지 않는다(FE-D009)
describe('인증 상태', () => {
  it('인증된 상태에서 가입해도 인증 상태와 주체가 그대로다', async () => {
    signupSucceeds();
    server.use(http.get(`*/api${PROVISIONAL_ME_PATH}`, () => HttpResponse.json({ memberId: 'm-1' })));
    const client = createApiClient();
    const router = createMemoryRouter(appRoutes, { initialEntries: ['/signup'] });
    const user = userEvent.setup();
    render(
      <AuthProvider adapter={createProvisionalAuthAdapter(client)} client={client}>
        <AuthProbe />
        <RouterProvider router={router} />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('auth')).toHaveTextContent('authenticated:m-1'));
    await signUp(user);
    await screen.findByRole('heading', { name: '가입 신청이 완료됐습니다' });
    expect(screen.getByTestId('auth')).toHaveTextContent('authenticated:m-1');
  });
});

// FE-T029: 공개 경로와 링크(FE-D012, FE-U10)
describe('가입 경로', () => {
  it('/signup은 미인증(운영 어댑터)에서도 공개 경로로 보인다', async () => {
    renderAt(['/signup']);
    expect(await screen.findByRole('heading', { name: '회원가입' })).toBeInTheDocument();
  });

  it('홈과 로그인 화면에 가입 링크가 있다', async () => {
    const user = userEvent.setup();
    const router = renderAt(['/']);
    await user.click(await screen.findByRole('link', { name: '회원가입' }));
    expect(router.state.location.pathname).toBe('/signup');
    await router.navigate('/login');
    expect(await screen.findByRole('link', { name: '회원가입' })).toHaveAttribute('href', '/signup');
  });

  it('가입 화면에 로그인 링크가 있다', async () => {
    renderAt(['/signup']);
    expect(await screen.findByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login');
  });
});
