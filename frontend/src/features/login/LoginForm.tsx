import { useEffect, useRef, useState, type FormEvent } from 'react';
import type { ApiClient } from '../../shared/api/apiClient';
import { isApiError, type ApiFailure } from '../../shared/api/apiError';
import { classifyFormFailure } from '../../shared/api/formFailure';
import { retryMinutes } from '../../shared/time/deadline';
import styles from '../../shared/ui/AuthForm.module.css';
import { FormBanner } from '../../shared/ui/FormBanner';
import { errorBanner, statusBanner, useBanner, useRetryBlock } from '../../shared/ui/formFeedback';
import type { OtpRequired } from '../otp/otpApi';
import { submitLogin, type LoginInput } from './loginApi';
import { LOGIN_TEXT } from './loginText';

type LoginField = keyof LoginInput;

/**
 * 로그인 1차 인증 폼(FE-D037). 빈 값만 막고 형식·길이는 검사하지 않는다. 성공하면 onSuccess로 OTP 대기 정보를 넘긴다.
 * ready가 false(인증 초기화 중)면 제출하지 않는다(FE-D034).
 */
export function LoginForm({
  client,
  ready,
  onSuccess,
}: {
  client: ApiClient;
  ready: boolean;
  onSuccess: (result: OtpRequired) => void;
}) {
  const [values, setValues] = useState<LoginInput>({ username: '', password: '' });
  const [submitting, setSubmitting] = useState(false);
  const [focusTarget, setFocusTarget] = useState<{ field: LoginField } | null>(null);
  const { banner, bannerKey, show, clear } = useBanner();
  const retry = useRetryBlock();

  const valuesRef = useRef(values);
  const submittingRef = useRef(false);
  const abortRef = useRef<AbortController | null>(null);
  const inputs = useRef<Partial<Record<LoginField, HTMLInputElement | null>>>({});

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    if (focusTarget !== null) inputs.current[focusTarget.field]?.focus();
  }, [focusTarget]);

  function showFailure(apiFailure: ApiFailure) {
    const failure = classifyFormFailure(apiFailure);
    switch (failure.kind) {
      case 'silent':
        return;
      case 'csrf-retry':
        return show(errorBanner(LOGIN_TEXT.csrfRetry));
      case 'csrf-unavailable':
        return show(errorBanner(LOGIN_TEXT.csrfUnavailable));
      case 'transport':
        return show(errorBanner(LOGIN_TEXT.common));
      case 'too-many':
        if (failure.retryAfterSeconds === undefined) return show(statusBanner(LOGIN_TEXT.tooMany));
        show(statusBanner(LOGIN_TEXT.tooManyWithTime(retryMinutes(failure.retryAfterSeconds))));
        return retry.block(failure.retryAfterSeconds);
      case 'unavailable':
        return show(errorBanner(LOGIN_TEXT.retryLater));
      case 'rejected':
        // 형식이 틀린 입력(400)도 로그인 실패와 같다(MB-D011)
        if (failure.status === 401 || failure.status === 400) return show(errorBanner(LOGIN_TEXT.loginFailed));
        if (failure.status === 403 && failure.code === 'MEMBER_ACCOUNT_DISABLED') return show(errorBanner(LOGIN_TEXT.disabled));
        // 그 밖의 403·5xx도 권한 없음 화면으로 가지 않고 여기 머문다(FE-D040 (2))
        return show(errorBanner(LOGIN_TEXT.common));
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ready || submittingRef.current || retry.isBlocked()) return;
    const submitted = valuesRef.current;
    // 빈 값만 막는다(FE-D037). 비밀번호는 Trim하지 않으므로 공백만 있어도 서버에 보낸다(서버도 @NotNull만 본다)
    const empty: LoginField | undefined =
      submitted.username.trim() === '' ? 'username' : submitted.password === '' ? 'password' : undefined;
    if (empty !== undefined) {
      show(errorBanner(LOGIN_TEXT.empty));
      setFocusTarget({ field: empty });
      return;
    }
    clear();
    submittingRef.current = true;
    setSubmitting(true);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const result = await submitLogin(client, submitted, controller.signal);
      if (controller.signal.aborted) return;
      onSuccess(result);
    } catch (error) {
      if (controller.signal.aborted) return;
      if (isApiError(error)) showFailure(error.failure);
      else show(errorBanner(LOGIN_TEXT.common));
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
      if (!controller.signal.aborted) {
        submittingRef.current = false;
        setSubmitting(false);
      }
    }
  }

  function handleChange(name: LoginField, value: string) {
    valuesRef.current = { ...valuesRef.current, [name]: value };
    setValues(valuesRef.current);
  }

  const label = !ready ? LOGIN_TEXT.checking : submitting ? '로그인 중…' : retry.blocked ? '잠시 후 다시 시도' : '로그인';

  return (
    <form className={styles.form} noValidate onSubmit={handleSubmit} aria-busy={submitting}>
      <FormBanner banner={banner} bannerKey={bannerKey} />
      <div className={styles.field}>
        <label htmlFor="login-username" className={styles.label}>
          아이디
        </label>
        <input
          id="login-username"
          ref={(el) => {
            inputs.current.username = el;
          }}
          className={styles.input}
          name="username"
          type="text"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          value={values.username}
          onChange={(e) => handleChange('username', e.target.value)}
        />
      </div>
      <div className={styles.field}>
        <label htmlFor="login-password" className={styles.label}>
          비밀번호
        </label>
        <input
          id="login-password"
          ref={(el) => {
            inputs.current.password = el;
          }}
          className={styles.input}
          name="password"
          type="password"
          autoComplete="current-password"
          value={values.password}
          onChange={(e) => handleChange('password', e.target.value)}
        />
      </div>
      <button type="submit" className={styles.submit} disabled={!ready || submitting || retry.blocked}>
        {label}
      </button>
    </form>
  );
}
