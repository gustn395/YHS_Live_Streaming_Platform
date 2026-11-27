import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import type { ApiClient } from '../../shared/api/apiClient';
import { isApiError, type ApiFailure } from '../../shared/api/apiError';
import { classifyFormFailure } from '../../shared/api/formFailure';
import { deadlineAfter, remainingUntil, retryMinutes, waitUntil } from '../../shared/time/deadline';
import styles from '../../shared/ui/AuthForm.module.css';
import { FormBanner } from '../../shared/ui/FormBanner';
import { errorBanner, statusBanner, useBanner, useRetryBlock } from '../../shared/ui/formFeedback';
import { RESEND_INTERVAL_SECONDS, resendOtp, verifyOtp, type LoginPrincipal, type OtpRequired } from './otpApi';
import { OTP_TEXT, type OtpRestartReason } from './otpText';

export interface OtpFormProps {
  client: ApiClient;
  /** 인증 초기화가 끝났는지. 끝나기 전에는 확인하지 않는다(FE-D034). */
  ready: boolean;
  /** OTP 만료 시각(ms). 모르면(Google 진입 등) null이고 남은 시간을 표시하지 않는다(FE-D035). */
  otpExpiresAt: number | null;
  /** 재발송을 다시 할 수 있는 시각(ms). 모르면 null이고 바로 재발송할 수 있다(서버 429가 최종 판단). */
  resendAvailableAt: number | null;
  /** 확인 성공. 화면의 로그인 상태에 반영했으면 true, 반영하지 못했으면 false(FE-D042 (1)). */
  onVerified: (principal: LoginPrincipal) => boolean;
  onResent: (result: OtpRequired, resendAvailableAt: number) => void;
  onRestart: (reason: OtpRestartReason) => void;
}

const OTP_LENGTH = 4;

type Pending = 'verify' | 'resend' | null;

/** 마감 시각까지 남은 ms를 1초마다 갱신한다. 마감 시각을 모르면 null. reset은 이벤트 처리 중에만 부른다. */
function useCountdown(initial: number | null) {
  const [deadline, setDeadline] = useState(initial);
  const [remaining, setRemaining] = useState<number | null>(() => (initial === null ? null : remainingUntil(initial)));
  useEffect(() => {
    if (deadline === null) return undefined;
    return waitUntil(deadline, () => setRemaining(0), { tickMs: 1000, onTick: setRemaining });
  }, [deadline]);
  const reset = useCallback((next: number) => {
    setDeadline(next);
    setRemaining(remainingUntil(next));
  }, []);
  return [remaining, reset] as const;
}

/**
 * 이메일 OTP 확인 폼(FE-D035, FE-D037). 일반 로그인과 Google 로그인이 함께 쓴다. 대기 상태는 이 화면만 가진다.
 * 확인과 재발송은 동시에 나가지 않는다.
 */
export function OtpForm({ client, ready, otpExpiresAt, resendAvailableAt, onVerified, onResent, onRestart }: OtpFormProps) {
  const [code, setCode] = useState('');
  const [pending, setPending] = useState<Pending>(null);
  const [focusKey, setFocusKey] = useState(0);
  const { banner, bannerKey, show, clear } = useBanner();
  const verifyRetry = useRetryBlock();
  const [otpRemaining, resetOtpDeadline] = useCountdown(otpExpiresAt);
  const [resendRemaining, resetResendDeadline] = useCountdown(resendAvailableAt);

  const codeRef = useRef('');
  const pendingRef = useRef<Pending>(null);
  const resendAvailableAtRef = useRef(resendAvailableAt ?? 0);
  const abortRef = useRef<AbortController | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    if (focusKey > 0) inputRef.current?.focus();
  }, [focusKey]);

  function updateCode(value: string) {
    codeRef.current = value;
    setCode(value);
  }

  function delayResend(seconds: number) {
    const next = deadlineAfter(seconds);
    resendAvailableAtRef.current = next;
    resetResendDeadline(next);
    return next;
  }

  /** 실패를 안내한다. 처음부터 다시 해야 하는 실패는 onRestart로 넘기고 안내하지 않는다. */
  function showFailure(apiFailure: ApiFailure, action: 'verify' | 'resend') {
    const failure = classifyFormFailure(apiFailure);
    switch (failure.kind) {
      case 'silent':
        return;
      case 'csrf-retry':
        return show(errorBanner(OTP_TEXT.csrfRetry));
      case 'csrf-unavailable':
        return show(errorBanner(OTP_TEXT.csrfUnavailable));
      case 'transport':
        return show(errorBanner(OTP_TEXT.common));
      case 'too-many':
        if (failure.retryAfterSeconds === undefined) return show(statusBanner(OTP_TEXT.tooMany));
        show(statusBanner(OTP_TEXT.tooManyWithTime(retryMinutes(failure.retryAfterSeconds))));
        if (action === 'verify') verifyRetry.block(failure.retryAfterSeconds);
        else delayResend(failure.retryAfterSeconds);
        return;
      case 'unavailable':
        return show(errorBanner(OTP_TEXT.retryLater));
      case 'rejected': {
        const { status, code: errorCode } = failure;
        if (status === 401 && errorCode === 'AUTH_OTP_CHALLENGE_INVALID') return onRestart('challengeInvalid');
        if (status === 401 && errorCode === 'AUTH_LOGIN_FAILED') return onRestart('loginFailed');
        if (status === 403 && errorCode === 'MEMBER_ACCOUNT_DISABLED') return onRestart('disabled');
        if (status === 401 && errorCode === 'AUTH_OTP_MISMATCH') {
          updateCode('');
          setFocusKey((k) => k + 1);
          return show(errorBanner(OTP_TEXT.mismatch));
        }
        if (status === 401 && errorCode === 'AUTH_OTP_EXPIRED') {
          updateCode('');
          return show(errorBanner(OTP_TEXT.expired));
        }
        // 그 밖의 403도 권한 없음 화면으로 가지 않고 여기 머문다(FE-D040 (2))
        return show(errorBanner(OTP_TEXT.common));
      }
    }
  }

  /** 요청 하나를 실행한다. done이 true를 돌려주면 화면을 떠나므로 진행 중 상태를 풀지 않는다. */
  async function run<T>(action: 'verify' | 'resend', call: (signal: AbortSignal) => Promise<T>, done: (result: T) => boolean) {
    pendingRef.current = action;
    setPending(action);
    clear();
    const controller = new AbortController();
    abortRef.current = controller;
    let leaving = false;
    try {
      const result = await call(controller.signal);
      if (controller.signal.aborted) return;
      leaving = done(result);
    } catch (error) {
      if (controller.signal.aborted) return;
      if (isApiError(error)) showFailure(error.failure, action);
      else show(errorBanner(OTP_TEXT.common));
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
      if (!controller.signal.aborted && !leaving) {
        pendingRef.current = null;
        setPending(null);
      }
    }
  }

  async function handleVerify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const submitted = codeRef.current;
    if (!ready || pendingRef.current !== null || submitted.length !== OTP_LENGTH || verifyRetry.isBlocked()) return;
    await run(
      'verify',
      (signal) => verifyOtp(client, submitted, signal),
      (principal) => {
        if (onVerified(principal)) return true;
        // 확인은 됐지만 화면의 로그인 상태를 바꾸지 못했다. 보호 화면으로 가지 않는다(FE-D042 (1))
        show(errorBanner(OTP_TEXT.loginNotApplied));
        return false;
      },
    );
  }

  async function handleResend() {
    if (!ready || pendingRef.current !== null || remainingUntil(resendAvailableAtRef.current) > 0) return;
    await run(
      'resend',
      (signal) => resendOtp(client, signal),
      (result) => {
        updateCode('');
        resetOtpDeadline(result.otpExpiresAt);
        const availableAt = delayResend(RESEND_INTERVAL_SECONDS);
        show(statusBanner(OTP_TEXT.resent));
        onResent(result, availableAt);
        return false;
      },
    );
  }

  const resendWaiting = resendRemaining !== null && resendRemaining > 0;
  const verifyLabel = pending === 'verify' ? '확인 중…' : verifyRetry.blocked ? '잠시 후 다시 시도' : '확인';
  const resendLabel =
    pending === 'resend' ? '보내는 중…' : resendWaiting ? OTP_TEXT.resendWait(Math.ceil(resendRemaining / 1000)) : OTP_TEXT.resend;

  return (
    <form className={styles.form} noValidate onSubmit={handleVerify} aria-busy={pending !== null}>
      <FormBanner banner={banner} bannerKey={bannerKey} />
      <div className={styles.field}>
        <label htmlFor="otp-code" className={styles.label}>
          인증 번호
        </label>
        <input
          id="otp-code"
          ref={inputRef}
          className={`${styles.input} ${styles.otpInput}`}
          name="code"
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          // maxLength를 두지 않는다: 브라우저가 숫자를 거르기 전 원문을 잘라 붙여넣은 번호가 깨진다. 4자리 제한은 아래에서 한다
          aria-describedby="otp-code-hint"
          value={code}
          onChange={(e) => updateCode(e.target.value.replace(/[^0-9]/g, '').slice(0, OTP_LENGTH))}
        />
        <p id="otp-code-hint" className={styles.hint}>
          {otpRemaining === null
            ? OTP_TEXT.inputHint
            : otpRemaining > 0
              ? OTP_TEXT.remaining(otpRemaining)
              : OTP_TEXT.expiredHint}
        </p>
      </div>
      <button
        type="submit"
        className={styles.submit}
        disabled={!ready || pending !== null || verifyRetry.blocked || code.length !== OTP_LENGTH}
      >
        {verifyLabel}
      </button>
      <button
        type="button"
        className={styles.secondary}
        disabled={!ready || pending !== null || resendWaiting}
        onClick={() => void handleResend()}
      >
        {resendLabel}
      </button>
    </form>
  );
}
