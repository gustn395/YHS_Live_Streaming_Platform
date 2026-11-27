import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse, type JsonBodyType } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../../test/msw/server';
import { createApiClient } from '../../shared/api/apiClient';
import { OtpForm, type OtpFormProps } from './OtpForm';
import { OTP_TEXT } from './otpText';

const VERIFY = '*/api/v1/auth/otp/verify';
const RESEND = '*/api/v1/auth/otp/resend';
const PROBLEM_JSON = { 'Content-Type': 'application/problem+json' };
const NOW = Date.parse('2026-11-27T10:00:00Z');
const SUCCESS = { memberId: 7, role: 'USER', accessTokenExpiresAt: '2026-11-27T10:15:00Z' };

function renderForm(props: Partial<OtpFormProps> = {}) {
  document.cookie = 'XSRF-TOKEN=t1; path=/';
  const handlers = { onVerified: vi.fn(), onResent: vi.fn(), onRestart: vi.fn() };
  const user = userEvent.setup(vi.isFakeTimers() ? { advanceTimers: vi.advanceTimersByTime } : {});
  const utils = render(
    <OtpForm client={createApiClient()} ready otpExpiresAt={null} resendAvailableAt={null} {...handlers} {...props} />,
  );
  return { ...utils, ...handlers, user };
}

const input = () => screen.getByLabelText('인증 번호', { selector: 'input' });
const verifyButton = () => screen.getByRole('button', { name: /^확인|확인 중|잠시 후/ });
const resendButton = () => screen.getByRole('button', { name: /다시 받기|보내는 중/ });

function respond(path: string, status: number, body: JsonBodyType, headers: Record<string, string> = PROBLEM_JSON) {
  const bodies: string[] = [];
  server.use(
    http.post(path, async ({ request }) => {
      bodies.push(await request.text());
      return HttpResponse.json(body, { status, headers });
    }),
  );
  return bodies;
}

afterEach(() => {
  vi.useRealTimers();
});

// FE-T035: OTP 입력(FE-D035)
describe('OTP 입력', () => {
  it('입력 칸 하나, numeric·one-time-code 속성', () => {
    renderForm();
    expect(screen.getAllByRole('textbox')).toHaveLength(1);
    expect(input()).toHaveAttribute('inputmode', 'numeric');
    expect(input()).toHaveAttribute('autocomplete', 'one-time-code');
  });

  it('숫자가 아닌 문자는 거르고 4자리로 자르며 앞자리 0을 지킨다(붙여넣기 포함)', async () => {
    const { user } = renderForm();
    await user.click(input());
    await user.paste('0a4 2-0 9');
    expect(input()).toHaveValue('0420');
  });

  it('4자리가 되기 전에는 확인 버튼이 꺼져 있고, 4자리여도 자동 제출하지 않는다', async () => {
    const bodies = respond(VERIFY, 200, SUCCESS, {});
    const { user } = renderForm();
    await user.type(input(), '042');
    expect(verifyButton()).toBeDisabled();
    await user.type(input(), '0');
    expect(verifyButton()).toBeEnabled();
    expect(bodies).toHaveLength(0);
  });

  it('확인하면 문자열 code를 보내고 성공 principal을 넘긴다', async () => {
    const bodies = respond(VERIFY, 200, SUCCESS, {});
    const { user, onVerified } = renderForm();
    await user.type(input(), '0420');
    await user.click(verifyButton());
    await waitFor(() =>
      expect(onVerified).toHaveBeenCalledWith({ memberId: 7, role: 'USER', accessTokenExpiresAt: Date.parse(SUCCESS.accessTokenExpiresAt) }),
    );
    expect(bodies).toEqual(['{"code":"0420"}']);
  });
});

// FE-T036: OTP 확인 결과(FE-D037, FE-D040 (2))
describe('OTP 확인 실패', () => {
  it('일치하지 않으면 입력을 지우고 포커스하며 남은 횟수는 말하지 않는다', async () => {
    respond(VERIFY, 401, { code: 'AUTH_OTP_MISMATCH' });
    const { user } = renderForm();
    await user.type(input(), '1234');
    await user.click(verifyButton());
    expect(await screen.findByRole('alert')).toHaveTextContent(OTP_TEXT.mismatch);
    expect(screen.getByRole('alert').textContent).not.toMatch(/\d/);
    expect(input()).toHaveValue('');
    expect(input()).toHaveFocus();
  });

  it('만료되면 재발송을 안내한다', async () => {
    respond(VERIFY, 401, { code: 'AUTH_OTP_EXPIRED' });
    const { user } = renderForm();
    await user.type(input(), '1234');
    await user.click(verifyButton());
    expect(await screen.findByRole('alert')).toHaveTextContent(OTP_TEXT.expired);
  });

  it.each<[string, number, string, string]>([
    ['Challenge 없음', 401, 'AUTH_OTP_CHALLENGE_INVALID', 'challengeInvalid'],
    ['OTP 단계 로그인 실패', 401, 'AUTH_LOGIN_FAILED', 'loginFailed'],
    ['계정 사용 중지', 403, 'MEMBER_ACCOUNT_DISABLED', 'disabled'],
  ])('%s → 로그인 화면으로 다시 시작(사유 %s)', async (_name, status, code, reason) => {
    respond(VERIFY, status, { code });
    const { user, onRestart } = renderForm();
    await user.type(input(), '1234');
    await user.click(verifyButton());
    await waitFor(() => expect(onRestart).toHaveBeenCalledWith(reason));
  });

  it.each<[string, number, JsonBodyType, string]>([
    ['CSRF 403', 403, { code: 'AUTH_CSRF_INVALID' }, OTP_TEXT.csrfRetry],
    ['그 밖의 403은 머물러 공통 배너', 403, { code: 'OTHER' }, OTP_TEXT.common],
    ['503', 503, { code: 'AUTH_SESSION_STORE_UNAVAILABLE' }, OTP_TEXT.retryLater],
  ])('%s는 화면에 머물고 입력을 유지한다', async (_name, status, body, text) => {
    respond(VERIFY, status, body);
    const { user, onRestart } = renderForm();
    await user.type(input(), '1234');
    await user.click(verifyButton());
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
    expect(input()).toHaveValue('1234');
    expect(onRestart).not.toHaveBeenCalled();
  });

  it('429는 Retry-After 동안 확인 버튼을 막는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    respond(VERIFY, 429, { code: 'AUTH_OTP_RATE_LIMITED' }, { ...PROBLEM_JSON, 'Retry-After': '90' });
    const { user } = renderForm();
    await user.type(input(), '1234');
    await user.click(verifyButton());
    expect(await screen.findByRole('status')).toHaveTextContent(OTP_TEXT.tooManyWithTime(2));
    expect(verifyButton()).toBeDisabled();
    // 확인 429는 확인만 막는다. 재발송은 그대로 할 수 있다
    expect(resendButton()).toBeEnabled();
    act(() => {
      vi.advanceTimersByTime(90_000);
    });
    await waitFor(() => expect(verifyButton()).toBeEnabled());
  });
});

// FE-T037: 재발송·타이머·동시성(FE-D035)
describe('재발송과 남은 시간', () => {
  it('재발송 가능 시각 전에는 버튼을 막고 남은 초를 보이며, 지나면 켠다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    renderForm({ resendAvailableAt: NOW + 60_000 });
    expect(resendButton()).toBeDisabled();
    expect(resendButton()).toHaveTextContent(OTP_TEXT.resendWait(60));
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(resendButton()).toHaveTextContent(OTP_TEXT.resendWait(59));
    act(() => {
      vi.advanceTimersByTime(59_000);
    });
    expect(resendButton()).toBeEnabled();
    expect(resendButton()).toHaveTextContent(OTP_TEXT.resend);
  });

  it('발급 시각을 모르는 진입(Google·state 없음)은 바로 재발송할 수 있고 남은 시간을 표시하지 않는다', () => {
    renderForm();
    expect(resendButton()).toBeEnabled();
    expect(screen.queryByText(/남은 시간/)).not.toBeInTheDocument();
  });

  it('OTP 남은 시간을 분:초로 보이고 지나면 만료 안내를 보인다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    renderForm({ otpExpiresAt: NOW + 125_000 });
    expect(screen.getByText(OTP_TEXT.remaining(125_000))).toBeInTheDocument();
    expect(OTP_TEXT.remaining(125_000)).toContain('2:05');
    act(() => {
      vi.advanceTimersByTime(125_000);
    });
    expect(screen.getByText(OTP_TEXT.expiredHint)).toBeInTheDocument();
  });

  it('재발송에 성공하면 새 만료 시각과 60초 대기를 반영하고 입력을 비운다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(NOW);
    const bodies = respond(RESEND, 200, { status: 'OTP_REQUIRED', otpExpiresAt: '2026-11-27T10:05:00Z' }, {});
    const { user, onResent } = renderForm();
    await user.type(input(), '12');
    await user.click(resendButton());
    expect(await screen.findByRole('status')).toHaveTextContent(OTP_TEXT.resent);
    expect(bodies).toEqual(['']);
    expect(onResent).toHaveBeenCalledTimes(1);
    const [result, availableAt] = onResent.mock.calls[0] as [{ otpExpiresAt: number }, number];
    expect(result).toEqual({ otpExpiresAt: Date.parse('2026-11-27T10:05:00Z') });
    expect(availableAt - Date.now()).toBeGreaterThan(59_000);
    expect(resendButton()).toBeDisabled();
    expect(input()).toHaveValue('');
  });

  it('재발송에서 Challenge가 없으면 로그인 화면으로 다시 시작한다', async () => {
    respond(RESEND, 401, { code: 'AUTH_OTP_CHALLENGE_INVALID' });
    const { user, onRestart } = renderForm();
    await user.click(resendButton());
    await waitFor(() => expect(onRestart).toHaveBeenCalledWith('challengeInvalid'));
  });

  it('재발송 429는 Retry-After 동안 재발송만 막는다', async () => {
    respond(RESEND, 429, { code: 'AUTH_OTP_RATE_LIMITED' }, { ...PROBLEM_JSON, 'Retry-After': '30' });
    const { user } = renderForm();
    await user.type(input(), '1234');
    await user.click(resendButton());
    expect(await screen.findByRole('status')).toHaveTextContent(OTP_TEXT.tooManyWithTime(1));
    expect(resendButton()).toBeDisabled();
    expect(verifyButton()).toBeEnabled();
  });

  it('확인과 재발송은 동시에 나가지 않는다(진행 중에는 둘 다 막힘)', async () => {
    let verifies = 0;
    let resends = 0;
    server.use(
      http.post(VERIFY, async () => {
        verifies += 1;
        await delay(50);
        return HttpResponse.json({ code: 'AUTH_OTP_MISMATCH' }, { status: 401, headers: PROBLEM_JSON });
      }),
      http.post(RESEND, () => {
        resends += 1;
        return HttpResponse.json({ status: 'OTP_REQUIRED', otpExpiresAt: '2026-11-27T10:05:00Z' });
      }),
    );
    const { user } = renderForm();
    await user.type(input(), '1234');
    const form = verifyButton().closest('form') as HTMLFormElement;
    fireEvent.submit(form);
    fireEvent.submit(form);
    fireEvent.click(resendButton());
    expect(resendButton()).toBeDisabled();
    await screen.findByRole('alert');
    expect(verifies).toBe(1);
    expect(resends).toBe(0);
  });

  it('인증 상태 확인 중(ready=false)에는 확인하지 않는다', async () => {
    const bodies = respond(VERIFY, 200, SUCCESS, {});
    const { user } = renderForm({ ready: false });
    await user.type(input(), '1234');
    expect(verifyButton()).toBeDisabled();
    fireEvent.submit(verifyButton().closest('form') as HTMLFormElement);
    expect(bodies).toHaveLength(0);
  });

  it('화면을 떠나면 남은 시간·재발송 타이머를 남기지 않는다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    const { unmount } = renderForm({ otpExpiresAt: NOW + 300_000, resendAvailableAt: NOW + 60_000 });
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('화면을 떠나면 진행 중 확인 요청의 결과를 반영하지 않는다', async () => {
    server.use(
      http.post(VERIFY, async () => {
        await delay(100);
        return HttpResponse.json(SUCCESS);
      }),
    );
    const { user, unmount, onVerified } = renderForm();
    await user.type(input(), '1234');
    await user.click(verifyButton());
    unmount();
    await new Promise((r) => setTimeout(r, 150));
    expect(onVerified).not.toHaveBeenCalled();
  });
});
