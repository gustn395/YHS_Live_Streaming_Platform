import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, type JsonBodyType } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw/server';
import { createApiClient } from '../../shared/api/apiClient';
import { SignupForm } from './SignupForm';
import { MESSAGES } from './signupValidation';

const SIGNUP = '*/api/v1/auth/signup';
const PROBLEM_JSON = { 'Content-Type': 'application/problem+json' };
const VALID = {
  username: 'tester01',
  email: 'tester@example.com',
  nickname: '테스터',
  password: 'correct horse battery staple',
};

function renderForm(onSuccess = vi.fn()) {
  document.cookie = 'XSRF-TOKEN=t1; path=/';
  const user = userEvent.setup();
  render(<SignupForm client={createApiClient()} onSuccess={onSuccess} />);
  return { user, onSuccess };
}

const field = (name: RegExp | string) => screen.getByLabelText(name, { selector: 'input' });

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(field('아이디'), VALID.username);
  await user.type(field('이메일'), VALID.email);
  await user.type(field('닉네임'), VALID.nickname);
  await user.type(field(/^비밀번호$/), VALID.password);
  await user.type(field('비밀번호 확인'), VALID.password);
}

const submit = () => screen.getByRole('button', { name: /가입하기|가입 중|잠시 후/ });

function respond(status: number, body: JsonBodyType, headers: Record<string, string> = PROBLEM_JSON) {
  let calls = 0;
  server.use(
    http.post(SIGNUP, () => {
      calls += 1;
      return HttpResponse.json(body, { status, headers });
    }),
  );
  return () => calls;
}

afterEach(() => {
  vi.useRealTimers();
});

// FE-T026: 검사 시점(FE-D022)
describe('검사 시점', () => {
  it('입력 중에는 오류를 띄우지 않고 필드를 벗어나면 검사한다', async () => {
    const { user } = renderForm();
    await user.type(field('아이디'), 'ab');
    expect(screen.queryByText(MESSAGES.usernamePattern)).not.toBeInTheDocument();
    await user.tab();
    expect(screen.getByText(MESSAGES.usernamePattern)).toBeInTheDocument();
  });

  it('오류가 표시된 필드는 입력할 때마다 다시 검사해 고치면 오류가 사라진다', async () => {
    const { user } = renderForm();
    await user.type(field('아이디'), 'ab');
    await user.tab();
    await user.type(field('아이디'), 'cd');
    expect(screen.queryByText(MESSAGES.usernamePattern)).not.toBeInTheDocument();
  });

  it('제출하면 전체를 검사하고 오류 요약을 alert로 보이며 첫 오류 필드로 포커스를 옮긴다', async () => {
    const calls = respond(201, { memberId: 1, accountStatus: 'PENDING' }, {});
    const { user } = renderForm();
    await user.type(field('닉네임'), '테스터');
    await user.click(submit());
    expect(screen.getByRole('alert')).toHaveTextContent('입력값을 확인해 주세요');
    expect(field('아이디')).toHaveFocus();
    expect(calls()).toBe(0);
  });
});

// FE-T028: 접근성·비밀번호 필드(FE-D023, FE-D028)
describe('접근성', () => {
  it('오류는 aria-invalid와 aria-describedby로 필드에 연결된다', async () => {
    const { user } = renderForm();
    await user.type(field('아이디'), 'ab');
    await user.tab();
    const input = field('아이디');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription(expect.stringContaining(MESSAGES.usernamePattern));
  });

  it('autocomplete 값을 둔다', () => {
    renderForm();
    expect(field('아이디')).toHaveAttribute('autocomplete', 'username');
    expect(field('이메일')).toHaveAttribute('autocomplete', 'email');
    expect(field(/^비밀번호$/)).toHaveAttribute('autocomplete', 'new-password');
    expect(field('비밀번호 확인')).toHaveAttribute('autocomplete', 'new-password');
  });

  it('비밀번호 표시 토글은 필드마다 따로 동작한다', async () => {
    const { user } = renderForm();
    const [toggle, confirmToggle] = screen.getAllByRole('button', { name: /표시/ });
    expect(field(/^비밀번호$/)).toHaveAttribute('type', 'password');
    await user.click(toggle!);
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(field(/^비밀번호$/)).toHaveAttribute('type', 'text');
    expect(field('비밀번호 확인')).toHaveAttribute('type', 'password');
    expect(confirmToggle).toHaveAttribute('aria-pressed', 'false');
  });

  // CR-4(FE-03 Phase 5 Q1=a): 보이는 글자는 늘 "표시", 상태는 aria-pressed, 이름은 보이는 글자를 포함하고 필드별로 다르다
  it('토글을 눌러도 보이는 글자와 접근 가능한 이름은 바뀌지 않고 상태만 aria-pressed로 바뀐다', async () => {
    const { user } = renderForm();
    const toggle = screen.getByRole('button', { name: '비밀번호 표시' });
    const confirmToggle = screen.getByRole('button', { name: '비밀번호 확인 표시' });
    expect(toggle).toHaveTextContent(/^표시$/);
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await user.click(toggle);
    expect(toggle).toHaveTextContent(/^표시$/);
    expect(toggle).toHaveAccessibleName('비밀번호 표시');
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(confirmToggle).toHaveTextContent(/^표시$/);
    expect(confirmToggle).toHaveAttribute('aria-pressed', 'false');
  });

  it('비밀번호 붙여넣기를 막지 않는다', async () => {
    const { user } = renderForm();
    await user.click(field(/^비밀번호$/));
    await user.paste('pasted long passphrase');
    expect(field(/^비밀번호$/)).toHaveValue('pasted long passphrase');
  });

  it('비밀번호 길이 안내를 항상 보인다', () => {
    renderForm();
    expect(field(/^비밀번호$/)).toHaveAccessibleDescription(expect.stringContaining('15~64자'));
  });

  it('username에 대문자가 있으면 소문자로 바뀐다고 안내하고 입력값은 그대로 둔다', async () => {
    const { user } = renderForm();
    await user.type(field('아이디'), 'Tester01');
    expect(field('아이디')).toHaveValue('Tester01');
    expect(screen.getByText(/소문자로 바뀌어 가입됩니다: tester01/)).toBeInTheDocument();
  });
});

// FE-T026: 서버 응답 처리(FE-D021, FE-D024, FE-D025, FE-D031)
describe('서버 응답', () => {
  it('성공하면 onSuccess에 결과를 넘긴다', async () => {
    respond(201, { memberId: 9, accountStatus: 'PENDING' }, {});
    const { user, onSuccess } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await waitFor(() => expect(onSuccess).toHaveBeenCalledWith({ memberId: 9, accountStatus: 'PENDING' }));
  });

  it('처리 중에는 제출을 하나만 받는다', async () => {
    let calls = 0;
    let release: () => void = () => {};
    server.use(
      http.post(SIGNUP, async () => {
        calls += 1;
        await new Promise<void>((r) => (release = r));
        return HttpResponse.json({ memberId: 1, accountStatus: 'PENDING' }, { status: 201 });
      }),
    );
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(submit()).toBeDisabled();
    expect(submit()).toHaveTextContent('가입 중');
    await user.click(submit());
    await act(async () => release());
    expect(calls).toBe(1);
  });

  it('400 errors[]는 해당 필드에 서버 문구로 표시한다', async () => {
    respond(400, { code: 'COMMON_INVALID_INPUT', errors: [{ field: 'email', message: '올바른 형식의 이메일 주소여야 합니다' }] });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText('올바른 형식의 이메일 주소여야 합니다')).toBeInTheDocument();
    expect(field('이메일')).toHaveAttribute('aria-invalid', 'true');
  });

  it('유출 비밀번호는 비밀번호 필드에 고정 문구로 표시하고 서버 detail은 보이지 않는다', async () => {
    respond(400, { code: 'MEMBER_PASSWORD_BREACHED', detail: 'internal-detail-should-not-show' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/알려진 유출 비밀번호/)).toBeInTheDocument();
    expect(screen.queryByText(/internal-detail-should-not-show/)).not.toBeInTheDocument();
  });

  it.each([
    ['MEMBER_USERNAME_DUPLICATE', '아이디', /이미 사용 중인 아이디/],
    ['MEMBER_EMAIL_DUPLICATE', '이메일', /이미 가입된 이메일/],
  ])('409 %s는 해당 필드에 표시하고 그 필드를 고치면 지운다', async (code, label, text) => {
    respond(409, { code });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(field(label)).toHaveValue(label === '아이디' ? VALID.username : VALID.email);
    await user.type(field(label), 'x');
    expect(screen.queryByText(text)).not.toBeInTheDocument();
  });

  it.each(['MEMBER_PASSWORD_CHECK_UNAVAILABLE', 'COMMON_RATE_LIMIT_UNAVAILABLE'])('503 %s는 재시도를 안내하고 입력을 유지한다', async (code) => {
    respond(503, { code });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/잠시 뒤 다시 시도/)).toBeInTheDocument();
    expect(field('아이디')).toHaveValue(VALID.username);
    expect(submit()).toBeEnabled();
  });

  it('CSRF 403은 권한 없음이 아니라 다시 시도를 안내하고 입력을 유지하며 재전송하지 않는다', async () => {
    const calls = respond(403, { code: 'AUTH_CSRF_INVALID' });
    server.use(
      http.get('*/api/v1/auth/csrf', () => {
        document.cookie = 'XSRF-TOKEN=t2; path=/';
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/보안 확인이 만료/)).toBeInTheDocument();
    expect(field(/^비밀번호$/)).toHaveValue(VALID.password);
    expect(calls()).toBe(1);
  });

  it('CSRF 토큰을 받을 수 없으면 쿠키 설정을 안내하고 입력을 유지한다', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    server.use(http.get('*/api/v1/auth/csrf', () => new HttpResponse(null, { status: 204 })));
    const user = userEvent.setup();
    render(<SignupForm client={createApiClient()} onSuccess={vi.fn()} />);
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/쿠키 설정/)).toBeInTheDocument();
    expect(field('닉네임')).toHaveValue(VALID.nickname);
  });

  it('network 오류는 이미 가입됐을 수 있다고 안내하고 재전송하지 않는다', async () => {
    let calls = 0;
    server.use(
      http.post(SIGNUP, () => {
        calls += 1;
        return HttpResponse.error();
      }),
    );
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/이미 가입됐을 수 있으니/)).toBeInTheDocument();
    expect(calls).toBe(1);
  });

  it('비정상 201은 이미 가입됐을 수 있다고 안내한다', async () => {
    respond(201, { memberId: 'x' }, {});
    const { user, onSuccess } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/이미 가입됐을 수 있으니/)).toBeInTheDocument();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('429는 Retry-After 동안 버튼을 막고, 시간이 지나면 다시 누를 수 있다(자동 제출 없음)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const calls = respond(429, { code: 'COMMON_TOO_MANY_REQUESTS' }, { ...PROBLEM_JSON, 'Retry-After': '120' });
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<SignupForm client={createApiClient()} onSuccess={vi.fn()} />);
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/약 2분 뒤 다시 시도/)).toBeInTheDocument();
    expect(submit()).toBeDisabled();
    await act(async () => {
      vi.advanceTimersByTime(120_000);
    });
    expect(submit()).toBeEnabled();
    expect(calls()).toBe(1);
  });

  it('Retry-After가 없으면 시간 없이 안내하고 버튼을 막지 않는다', async () => {
    respond(429, { code: 'COMMON_TOO_MANY_REQUESTS' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/요청이 많습니다/)).toBeInTheDocument();
    expect(screen.queryByText(/분 뒤/)).not.toBeInTheDocument();
    expect(submit()).toBeEnabled();
  });
});

/** 응답을 테스트가 직접 내보내게 한다(요청이 도착한 뒤 사용자가 입력을 바꾸는 상황). */
function heldResponse(status: number, body: JsonBodyType, headers: Record<string, string> = PROBLEM_JSON) {
  let calls = 0;
  let release: () => void = () => {};
  let arrivedResolve: () => void = () => {};
  const arrived = new Promise<void>((r) => (arrivedResolve = r));
  server.use(
    http.post(SIGNUP, async () => {
      calls += 1;
      arrivedResolve();
      await new Promise<void>((r) => (release = r));
      return HttpResponse.json(body, { status, headers });
    }),
  );
  return { arrived, release: () => act(async () => release()), calls: () => calls };
}

// FE-T026(보완): 요청 뒤 바뀐 입력에 늦은 서버 오류를 붙이지 않는다(S6 리뷰 지적 1)
describe('늦은 서버 응답과 바뀐 입력', () => {
  it('409 아이디 중복은 제출 뒤 아이디를 바꿨으면 표시하지 않는다', async () => {
    const held = heldResponse(409, { code: 'MEMBER_USERNAME_DUPLICATE' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await held.arrived;
    fireEvent.change(field('아이디'), { target: { value: 'tester02' } });
    await held.release();
    // CR-2: 응답 처리가 끝났다는 증거(낡은 결과 안내 문구)를 확인한 뒤 부재를 단언한다
    expect(await screen.findByRole('status')).toHaveTextContent('입력이 바뀌어 이전 확인 결과는 표시하지 않았습니다');
    expect(screen.queryByText(/이미 사용 중인 아이디/)).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('전송 값이 같으면(대소문자만 바뀐 아이디) 409를 그대로 표시한다', async () => {
    const held = heldResponse(409, { code: 'MEMBER_USERNAME_DUPLICATE' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await held.arrived;
    fireEvent.change(field('아이디'), { target: { value: 'TESTER01' } });
    await held.release();
    expect(await screen.findByText(/이미 사용 중인 아이디/)).toBeInTheDocument();
  });

  it('409 이메일 중복은 Backend 정규화(앞뒤 공백·대소문자)가 같으면 표시한다', async () => {
    const held = heldResponse(409, { code: 'MEMBER_EMAIL_DUPLICATE' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await held.arrived;
    fireEvent.change(field('이메일'), { target: { value: ' Tester@Example.com ' } });
    await held.release();
    expect(await screen.findByText(/이미 가입된 이메일/)).toBeInTheDocument();
  });

  it('비밀번호 서버 오류는 아이디를 바꿨으면 표시하지 않는다(문맥 단어는 아이디에 의존)', async () => {
    const held = heldResponse(400, { code: 'COMMON_INVALID_INPUT', errors: [{ field: 'password', message: '서버 비밀번호 오류' }] });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await held.arrived;
    fireEvent.change(field('아이디'), { target: { value: 'tester02' } });
    await held.release();
    expect(await screen.findByRole('status')).toHaveTextContent('입력이 바뀌어 이전 확인 결과는 표시하지 않았습니다');
    expect(screen.queryByText('서버 비밀번호 오류')).not.toBeInTheDocument();
  });

  it('유출 비밀번호는 다른 필드만 바꿨으면 그대로 표시한다', async () => {
    const held = heldResponse(400, { code: 'MEMBER_PASSWORD_BREACHED' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await held.arrived;
    fireEvent.change(field('닉네임'), { target: { value: '다른닉네임' } });
    await held.release();
    expect(await screen.findByText(/알려진 유출 비밀번호/)).toBeInTheDocument();
  });

  it('적용할 오류가 하나도 없으면 고정 안내를 status로 보이고 필드 오류·포커스는 바꾸지 않는다(재리뷰 R3)', async () => {
    const held = heldResponse(409, { code: 'MEMBER_USERNAME_DUPLICATE' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await held.arrived;
    fireEvent.change(field('아이디'), { target: { value: 'tester02' } });
    field('닉네임').focus();
    await held.release();
    expect(await screen.findByRole('status')).toHaveTextContent('입력이 바뀌어 이전 확인 결과는 표시하지 않았습니다');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(field('아이디')).not.toHaveAttribute('aria-invalid');
    expect(field('닉네임')).toHaveFocus();
  });

  it('바꾼 필드의 오류만 빼고 나머지 필드 오류는 표시한다', async () => {
    const held = heldResponse(400, {
      code: 'COMMON_INVALID_INPUT',
      errors: [
        { field: 'email', message: '서버 이메일 오류' },
        { field: 'nickname', message: '서버 닉네임 오류' },
      ],
    });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await held.arrived;
    fireEvent.change(field('닉네임'), { target: { value: '다른닉네임' } });
    await held.release();
    expect(await screen.findByText('서버 이메일 오류')).toBeInTheDocument();
    expect(screen.queryByText('서버 닉네임 오류')).not.toBeInTheDocument();
  });
});

// FE-T026·FE-T028(보완): 서버 오류에도 요약을 알리고, 요약은 고정 문구라 입력 중에 다시 읽히지 않는다(지적 2·6)
describe('오류 요약 알림', () => {
  it.each<[string, number, JsonBodyType]>([
    ['409', 409, { code: 'MEMBER_USERNAME_DUPLICATE' }],
    ['유출 비밀번호', 400, { code: 'MEMBER_PASSWORD_BREACHED' }],
    ['400 errors[]', 400, { code: 'COMMON_INVALID_INPUT', errors: [{ field: 'email', message: '서버 이메일 오류' }] }],
  ])('%s 응답에도 오류 요약 alert를 보인다', async (_name, status, body) => {
    respond(status, body);
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByRole('alert')).toHaveTextContent('입력값을 확인해 주세요');
  });

  it('요약은 개수 없는 고정 문구라 입력을 고쳐도 내용이 바뀌지 않고, 오류가 모두 사라지면 숨는다', async () => {
    const { user } = renderForm();
    await user.type(field('닉네임'), '테스터');
    await user.click(submit());
    const before = screen.getByRole('alert').textContent;
    expect(before).not.toMatch(/\d/);
    await user.type(field('아이디'), VALID.username);
    expect(screen.getByRole('alert').textContent).toBe(before);
    await user.type(field('이메일'), VALID.email);
    await user.type(field(/^비밀번호$/), VALID.password);
    await user.type(field('비밀번호 확인'), VALID.password);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('오류를 모두 고친 뒤 제출 없이 다른 필드에서 오류가 생겨도 요약을 다시 알리지 않는다(재리뷰 R2)', async () => {
    const { user } = renderForm();
    await user.type(field('닉네임'), '테스터');
    await user.type(field('이메일'), VALID.email);
    await user.type(field(/^비밀번호$/), VALID.password);
    await user.type(field('비밀번호 확인'), VALID.password);
    await user.click(submit()); // 아이디만 비어 있어 실패
    expect(screen.getByRole('alert')).toBeInTheDocument();
    await user.type(field('아이디'), VALID.username);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await user.clear(field('닉네임'));
    await user.type(field('닉네임'), '가');
    await user.tab(); // blur로 닉네임 오류
    expect(screen.getByText(MESSAGES.nicknamePattern)).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('서버 오류가 남은 채 다시 제출해 다른 이유로 실패하면, 그 뒤 blur 오류로 요약을 다시 알리지 않는다', async () => {
    let calls = 0;
    server.use(
      http.post(SIGNUP, () => {
        calls += 1;
        return calls === 1
          ? HttpResponse.json({ code: 'MEMBER_USERNAME_DUPLICATE' }, { status: 409, headers: PROBLEM_JSON })
          : HttpResponse.error();
      }),
    );
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByRole('alert')).toHaveTextContent('입력값을 확인해 주세요');
    await user.click(submit()); // 같은 값으로 재제출 → network 실패(배너만)
    expect(await screen.findByText(/이미 가입됐을 수 있으니/)).toBeInTheDocument();
    await user.clear(field('닉네임'));
    await user.tab(); // blur로 닉네임 오류
    expect(screen.getByText(MESSAGES.nicknameRequired)).toBeInTheDocument();
    expect(screen.queryByText('입력값을 확인해 주세요. 표시된 항목을 고친 뒤 다시 가입하기를 눌러 주세요.')).not.toBeInTheDocument();
  });

  it('다시 제출해 또 실패하면 요약을 새로 알린다(새 alert 요소)', async () => {
    const { user } = renderForm();
    await user.click(submit());
    const first = screen.getByRole('alert');
    await user.click(submit());
    expect(screen.getByRole('alert')).not.toBe(first);
  });
});

// FE-T026(보완): 중복 제출 가드·언마운트 정리(지적 10)
describe('제출 가드와 정리', () => {
  it('렌더 사이 없이 여러 필드가 바뀌어도(자동완성) 앞의 입력이 덮이지 않는다(재리뷰 R1)', () => {
    renderForm();
    act(() => {
      fireEvent.change(field('아이디'), { target: { value: VALID.username } });
      fireEvent.change(field('이메일'), { target: { value: VALID.email } });
    });
    expect(field('아이디')).toHaveValue(VALID.username);
    expect(field('이메일')).toHaveValue(VALID.email);
  });

  it('서버 오류가 있는 두 필드가 렌더 사이 없이 바뀌면 두 오류가 모두 지워진다', async () => {
    respond(400, {
      code: 'COMMON_INVALID_INPUT',
      errors: [
        { field: 'username', message: '서버 아이디 오류' },
        { field: 'email', message: '서버 이메일 오류' },
      ],
    });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText('서버 아이디 오류')).toBeInTheDocument();
    act(() => {
      fireEvent.change(field('아이디'), { target: { value: 'tester02' } });
      fireEvent.change(field('이메일'), { target: { value: 'other@example.com' } });
    });
    expect(screen.queryByText('서버 아이디 오류')).not.toBeInTheDocument();
    expect(screen.queryByText('서버 이메일 오류')).not.toBeInTheDocument();
  });

  it('낡은 결과 안내는 다음 제출 때 지워진다', async () => {
    let calls = 0;
    let release: () => void = () => {};
    let arrivedResolve: () => void = () => {};
    const arrived = new Promise<void>((r) => (arrivedResolve = r));
    server.use(
      http.post(SIGNUP, async () => {
        calls += 1;
        if (calls === 1) {
          arrivedResolve();
          await new Promise<void>((r) => (release = r));
          return HttpResponse.json({ code: 'MEMBER_USERNAME_DUPLICATE' }, { status: 409, headers: PROBLEM_JSON });
        }
        return HttpResponse.json({ memberId: 1, accountStatus: 'PENDING' }, { status: 201 });
      }),
    );
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await arrived;
    fireEvent.change(field('아이디'), { target: { value: 'tester02' } });
    await act(async () => release());
    expect(await screen.findByRole('status')).toBeInTheDocument();
    await user.click(submit());
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());
  });

  it('처리 중 form submit 이벤트가 다시 와도(버튼 상태와 무관) 요청은 하나다', async () => {
    const held = heldResponse(201, { memberId: 1, accountStatus: 'PENDING' }, {});
    const { user } = renderForm();
    await fillValid(user);
    const form = field('아이디').closest('form')!;
    fireEvent.submit(form);
    fireEvent.submit(form);
    await held.arrived;
    fireEvent.submit(form);
    await held.release();
    expect(held.calls()).toBe(1);
  });

  it('요청 중에 화면을 떠나면 요청을 취소하고 onSuccess를 부르지 않는다', async () => {
    let aborted = false;
    let arrivedResolve: () => void = () => {};
    const arrived = new Promise<void>((r) => (arrivedResolve = r));
    server.use(
      http.post(SIGNUP, async ({ request }) => {
        arrivedResolve();
        await new Promise<void>((r) => request.signal.addEventListener('abort', () => r()));
        aborted = request.signal.aborted;
        return HttpResponse.error();
      }),
    );
    const errorSpy = vi.spyOn(console, 'error');
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    const view = render(<SignupForm client={createApiClient()} onSuccess={onSuccess} />);
    await fillValid(user);
    await user.click(submit());
    await arrived;
    view.unmount();
    await waitFor(() => expect(aborted).toBe(true));
    expect(onSuccess).not.toHaveBeenCalled();
    expect(errorSpy).not.toHaveBeenCalled();
  });
});

// FE-T026(Phase 5 CodeRabbit 반영)
describe('CodeRabbit 반영', () => {
  it('CR-3: 서버 오류가 와도 화면에서 더 앞에 있는 클라이언트 오류 필드로 포커스를 옮긴다', async () => {
    const held = heldResponse(400, { code: 'COMMON_INVALID_INPUT', errors: [{ field: 'email', message: '서버 이메일 오류' }] });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    await held.arrived;
    fireEvent.change(field('아이디'), { target: { value: 'ab' } });
    fireEvent.blur(field('아이디'));
    expect(screen.getByText(MESSAGES.usernamePattern)).toBeInTheDocument();
    await held.release();
    expect(await screen.findByText('서버 이메일 오류')).toBeInTheDocument();
    expect(field('아이디')).toHaveFocus();
  });

  it('CR-5: 서버 오류가 표시됐던 필드는 blur 없이도 입력할 때마다 다시 검사한다', async () => {
    respond(409, { code: 'MEMBER_USERNAME_DUPLICATE' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/이미 사용 중인 아이디/)).toBeInTheDocument();
    fireEvent.change(field('아이디'), { target: { value: 'ab' } });
    expect(screen.queryByText(/이미 사용 중인 아이디/)).not.toBeInTheDocument();
    expect(screen.getByText(MESSAGES.usernamePattern)).toBeInTheDocument();
  });

  it('CR-6: 201이 아닌 2xx(200)는 성공으로 보지 않고 결과 불명 안내만 한다', async () => {
    respond(200, { memberId: 9, accountStatus: 'PENDING' }, {});
    const { user, onSuccess } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/이미 가입됐을 수 있으니/)).toBeInTheDocument();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('CR-8(Q2=a): CSRF가 아닌 403은 화면에 머물러 공통 배너를 보이고 입력을 유지한다', async () => {
    respond(403, { code: 'AUTH_ACCESS_DENIED' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText('가입하지 못했습니다. 잠시 뒤 다시 시도해 주세요.')).toBeInTheDocument();
    expect(screen.queryByText(/보안 확인이 만료/)).not.toBeInTheDocument();
    expect(field('아이디')).toHaveValue(VALID.username);
  });

  it('CR-9: 중복 코드라도 409가 아니면 중복 안내를 하지 않는다', async () => {
    respond(400, { code: 'MEMBER_USERNAME_DUPLICATE' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText('가입하지 못했습니다. 잠시 뒤 다시 시도해 주세요.')).toBeInTheDocument();
    expect(screen.queryByText(/이미 사용 중인 아이디/)).not.toBeInTheDocument();
  });

  // S6 재리뷰 Q1=a: CR-9과 같은 원칙으로 유출 코드도 Backend 계약 상태(400)일 때만 필드 안내한다(FE-D032)
  it('유출 코드라도 400이 아니면 유출 안내를 하지 않고 공통 배너를 보인다', async () => {
    respond(409, { code: 'MEMBER_PASSWORD_BREACHED' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText('가입하지 못했습니다. 잠시 뒤 다시 시도해 주세요.')).toBeInTheDocument();
    expect(screen.queryByText(/알려진 유출 비밀번호/)).not.toBeInTheDocument();
  });

  it('CR-10: 아이디를 바꾸면 아이디에 의존하는 비밀번호 서버 오류(errors[])를 지운다', async () => {
    respond(400, { code: 'COMMON_INVALID_INPUT', errors: [{ field: 'password', message: '서버 비밀번호 오류' }] });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText('서버 비밀번호 오류')).toBeInTheDocument();
    fireEvent.change(field('아이디'), { target: { value: 'tester02' } });
    expect(screen.queryByText('서버 비밀번호 오류')).not.toBeInTheDocument();
  });

  it('CR-10: 비밀번호에만 의존하는 유출 오류는 아이디를 바꿔도 유지한다', async () => {
    respond(400, { code: 'MEMBER_PASSWORD_BREACHED' });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText(/알려진 유출 비밀번호/)).toBeInTheDocument();
    fireEvent.change(field('아이디'), { target: { value: 'tester02' } });
    expect(screen.getByText(/알려진 유출 비밀번호/)).toBeInTheDocument();
  });

  it('CR-10: 비밀번호를 바꾸면 비밀번호에도 의존하는 확인 서버 오류를 지운다', async () => {
    respond(400, { code: 'COMMON_INVALID_INPUT', errors: [{ field: 'passwordConfirm', message: '서버 확인 오류' }] });
    const { user } = renderForm();
    await fillValid(user);
    await user.click(submit());
    expect(await screen.findByText('서버 확인 오류')).toBeInTheDocument();
    fireEvent.change(field(/^비밀번호$/), { target: { value: `${VALID.password}!` } });
    expect(screen.queryByText('서버 확인 오류')).not.toBeInTheDocument();
  });
});

// FE-T026(보완): Retry-After 대기는 서버가 정한 시각까지 유지한다(지적 3)
describe('Retry-After 대기', () => {
  it('브라우저 타이머 최대값(2^31-1ms)보다 긴 대기도 그 시각 전에는 버튼을 풀지 않는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const seconds = 3_000_000; // 약 34.7일 > 2^31-1ms(약 24.8일)
    respond(429, { code: 'COMMON_TOO_MANY_REQUESTS' }, { ...PROBLEM_JSON, 'Retry-After': String(seconds) });
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<SignupForm client={createApiClient()} onSuccess={vi.fn()} />);
    await fillValid(user);
    await user.click(submit());
    await screen.findByText(/분 뒤 다시 시도/);
    await act(async () => {
      vi.advanceTimersByTime(2 ** 31);
    });
    expect(submit()).toBeDisabled();
    await act(async () => {
      vi.advanceTimersByTime(seconds * 1000 - 2 ** 31);
    });
    expect(submit()).toBeEnabled();
  });

  it('대기 중에는 form submit 이벤트로도 보내지 않는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const calls = respond(429, { code: 'COMMON_TOO_MANY_REQUESTS' }, { ...PROBLEM_JSON, 'Retry-After': '60' });
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<SignupForm client={createApiClient()} onSuccess={vi.fn()} />);
    await fillValid(user);
    await user.click(submit());
    await screen.findByText(/약 1분 뒤/);
    fireEvent.submit(field('아이디').closest('form')!);
    // 요청이 나갔다면 도착할 만큼 기다린 뒤 센다
    await act(async () => {
      await new Promise((r) => setTimeout(r, 200));
    });
    expect(calls()).toBe(1);
  });

  it('대기 중에 화면을 떠나면 타이머를 남기지 않는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    respond(429, { code: 'COMMON_TOO_MANY_REQUESTS' }, { ...PROBLEM_JSON, 'Retry-After': '120' });
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const view = render(<SignupForm client={createApiClient()} onSuccess={vi.fn()} />);
    await fillValid(user);
    // 대기 타이머 ID를 잡아 두고, 화면을 떠날 때 그 ID가 정리되는지 직접 본다(재리뷰 R4: 개수 차이는 환경 타이머에 흔들린다)
    const scheduled = vi.spyOn(globalThis, 'setTimeout');
    await user.click(submit());
    await screen.findByText(/약 2분 뒤/);
    const waitTimers = scheduled.mock.calls
      .map((call, i) => ({ delay: call[1], id: scheduled.mock.results[i]?.value as unknown }))
      .filter((t) => t.delay === 120_000)
      .map((t) => t.id);
    expect(waitTimers).toHaveLength(1);
    const cleared = vi.spyOn(globalThis, 'clearTimeout');
    view.unmount();
    expect(cleared).toHaveBeenCalledWith(waitTimers[0]);
  });
});
