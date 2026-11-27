import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse, type JsonBodyType } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw/server';
import { createApiClient } from '../../shared/api/apiClient';
import { LoginForm } from './LoginForm';
import { LOGIN_TEXT } from './loginText';

const LOGIN = '*/api/v1/auth/login';
const PROBLEM_JSON = { 'Content-Type': 'application/problem+json' };
const EXPIRES = '2026-11-27T10:05:00Z';
const OK = { status: 'OTP_REQUIRED', otpExpiresAt: EXPIRES };

function renderForm(props: { ready?: boolean } = {}) {
  document.cookie = 'XSRF-TOKEN=t1; path=/';
  const onSuccess = vi.fn();
  const user = userEvent.setup();
  const utils = render(<LoginForm client={createApiClient()} ready={props.ready ?? true} onSuccess={onSuccess} />);
  return { ...utils, user, onSuccess };
}

const field = (name: RegExp | string) => screen.getByLabelText(name, { selector: 'input' });
const submit = () => screen.getByRole('button', { name: /^로그인|로그인 중|잠시 후/ });

function respond(status: number, body: JsonBodyType, headers: Record<string, string> = PROBLEM_JSON) {
  const bodies: unknown[] = [];
  server.use(
    http.post(LOGIN, async ({ request }) => {
      bodies.push(await request.json());
      return HttpResponse.json(body, { status, headers });
    }),
  );
  return bodies;
}

async function fill(user: ReturnType<typeof userEvent.setup>, username = 'tester01', password = 'pw') {
  await user.type(field('아이디'), username);
  await user.type(field('비밀번호'), password);
}

afterEach(() => {
  vi.useRealTimers();
});

// FE-T033: 로그인 폼(FE-D037)
describe('로그인 폼 입력', () => {
  it('빈 값이면 요청하지 않고 안내하며 빈 첫 필드로 포커스를 옮긴다', async () => {
    const bodies = respond(200, OK, {});
    const { user } = renderForm();
    await user.type(field('비밀번호'), 'pw');
    await user.click(submit());
    expect(screen.getByRole('alert')).toHaveTextContent(LOGIN_TEXT.empty);
    expect(field('아이디')).toHaveFocus();
    expect(bodies).toHaveLength(0);
  });

  it('형식·길이를 검사하지 않고 입력 그대로 보낸다(대문자 아이디, 31자 이상·앞뒤 공백 비밀번호)', async () => {
    const bodies = respond(200, OK, {});
    const { user, onSuccess } = renderForm();
    const password = `  ${'x'.repeat(40)}  `;
    await fill(user, 'Tester_01', password);
    await user.click(submit());
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith({ otpExpiresAt: Date.parse(EXPIRES) }));
    expect(bodies).toEqual([{ username: 'Tester_01', password }]);
  });

  it('입력 필드 속성: autocomplete username·current-password, 비밀번호는 가린다', () => {
    renderForm();
    expect(field('아이디')).toHaveAttribute('autocomplete', 'username');
    expect(field('비밀번호')).toHaveAttribute('autocomplete', 'current-password');
    expect(field('비밀번호')).toHaveAttribute('type', 'password');
  });

  it('인증 상태를 확인하는 중(ready=false)에는 제출하지 않는다', async () => {
    const bodies = respond(200, OK, {});
    const { user } = renderForm({ ready: false });
    await fill(user);
    expect(submit()).toBeDisabled();
    fireEvent.submit(submit().closest('form') as HTMLFormElement);
    expect(bodies).toHaveLength(0);
  });

  it('처리 중에는 submit 이벤트가 여러 번 와도 요청은 1건이다', async () => {
    let calls = 0;
    server.use(
      http.post(LOGIN, async () => {
        calls += 1;
        await delay(50);
        return HttpResponse.json(OK);
      }),
    );
    const { user, onSuccess } = renderForm();
    await fill(user);
    const form = submit().closest('form') as HTMLFormElement;
    fireEvent.submit(form);
    fireEvent.submit(form);
    fireEvent.submit(form);
    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1));
    expect(calls).toBe(1);
  });
});

describe('로그인 실패 안내', () => {
  it.each<[string, number, JsonBodyType, string]>([
    ['401 로그인 실패', 401, { code: 'AUTH_LOGIN_FAILED' }, LOGIN_TEXT.loginFailed],
    ['400 입력 오류도 같은 문구', 400, { code: 'COMMON_INVALID_INPUT' }, LOGIN_TEXT.loginFailed],
    ['403 계정 사용 중지', 403, { code: 'MEMBER_ACCOUNT_DISABLED' }, LOGIN_TEXT.disabled],
    ['403 CSRF', 403, { code: 'AUTH_CSRF_INVALID' }, LOGIN_TEXT.csrfRetry],
    ['그 밖의 403은 화면에 머물러 공통 배너(FE-D040 (2))', 403, { code: 'SOMETHING_ELSE' }, LOGIN_TEXT.common],
    ['503 일시 장애', 503, { code: 'MEMBER_OTP_DELIVERY_UNAVAILABLE' }, LOGIN_TEXT.retryLater],
    ['500', 500, { code: 'COMMON_INTERNAL_ERROR' }, LOGIN_TEXT.common],
  ])('%s', async (_name, status, body, text) => {
    respond(status, body);
    const { user } = renderForm();
    await fill(user, 'tester01', ' secret ');
    await user.click(submit());
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
    // 입력값을 유지한다
    expect(field('아이디')).toHaveValue('tester01');
    expect(field('비밀번호')).toHaveValue(' secret ');
  });

  it('응답 형식 오류·network는 공통 문구', async () => {
    respond(200, { status: 'X' }, {});
    const { user, onSuccess } = renderForm();
    await fill(user);
    await user.click(submit());
    expect(await screen.findByRole('alert')).toHaveTextContent(LOGIN_TEXT.common);
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('429는 Retry-After 동안 버튼과 submit을 막고, 지나면 다시 누를 수 있다(자동 제출 없음)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const bodies = respond(429, { code: 'AUTH_LOGIN_TEMPORARILY_BLOCKED' }, { ...PROBLEM_JSON, 'Retry-After': '120' });
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<LoginForm client={createApiClient()} ready onSuccess={vi.fn()} />);
    await fill(user);
    await user.click(submit());
    expect(await screen.findByRole('status')).toHaveTextContent(LOGIN_TEXT.tooManyWithTime(2));
    expect(submit()).toBeDisabled();
    fireEvent.submit(submit().closest('form') as HTMLFormElement);
    expect(bodies).toHaveLength(1);
    act(() => {
      vi.advanceTimersByTime(120_000);
    });
    await waitFor(() => expect(submit()).toBeEnabled());
    expect(bodies).toHaveLength(1);
  });

  it('429에 Retry-After가 없으면 시간 없이 안내하고 막지 않는다', async () => {
    respond(429, { code: 'COMMON_TOO_MANY_REQUESTS' });
    const { user } = renderForm();
    await fill(user);
    await user.click(submit());
    expect(await screen.findByRole('status')).toHaveTextContent(LOGIN_TEXT.tooMany);
    expect(submit()).toBeEnabled();
  });

  it('다시 제출하면 이전 안내를 지운다', async () => {
    respond(401, { code: 'AUTH_LOGIN_FAILED' });
    const { user } = renderForm();
    await fill(user);
    await user.click(submit());
    await screen.findByRole('alert');
    respond(200, OK, {});
    await user.click(submit());
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  it('화면을 떠나면 진행 중 요청을 취소하고 결과를 반영하지 않는다', async () => {
    server.use(
      http.post(LOGIN, async () => {
        await delay(100);
        return HttpResponse.json(OK);
      }),
    );
    const { user, onSuccess, unmount } = renderForm();
    await fill(user);
    await user.click(submit());
    unmount();
    await new Promise((r) => setTimeout(r, 150));
    expect(onSuccess).not.toHaveBeenCalled();
  });
});
