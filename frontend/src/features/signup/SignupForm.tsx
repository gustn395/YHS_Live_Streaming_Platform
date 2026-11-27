import { useEffect, useRef, useState, type FormEvent } from 'react';
import type { ApiClient } from '../../shared/api/apiClient';
import { isApiError, type ApiFailure } from '../../shared/api/apiError';
import { presentApiFailure } from '../../shared/api/errorPresentation';
import { submitSignup, type SignupResult } from './signupApi';
import styles from './SignupForm.module.css';
import {
  javaStrip,
  normalizeUsername,
  SIGNUP_FIELDS,
  validateField,
  validateSignup,
  type SignupField,
  type SignupFieldErrors,
  type SignupInput,
} from './signupValidation';

const LABELS: Record<SignupField, string> = {
  username: '아이디',
  email: '이메일',
  nickname: '닉네임',
  password: '비밀번호',
  passwordConfirm: '비밀번호 확인',
};

/** 길이 안내는 비밀번호 필드에만 둔다(FE-D023). */
const HINTS: Partial<Record<SignupField, string>> = {
  username: '영문 소문자·숫자·_ 4~20자',
  nickname: '2~20자, 다른 사람과 같아도 됩니다',
  password: '15~64자, 문장처럼 길게 써도 됩니다',
};

const AUTOCOMPLETE: Record<SignupField, string> = {
  username: 'username',
  email: 'email',
  nickname: 'nickname',
  password: 'new-password',
  passwordConfirm: 'new-password',
};

/** 서버 오류 코드별 고정 문구(FE-D031). 서버의 detail·title은 표시하지 않는다. */
const TEXT = {
  summary: '입력값을 확인해 주세요. 표시된 항목을 고친 뒤 다시 가입하기를 눌러 주세요.',
  breached: '알려진 유출 비밀번호입니다. 다른 비밀번호를 써 주세요.',
  usernameDuplicate: '이미 사용 중인 아이디입니다.',
  emailDuplicate: '이미 가입된 이메일입니다.',
  retryLater: '일시적으로 가입을 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요.',
  csrfRetry: '보안 확인이 만료됐습니다. 입력한 내용을 확인하고 다시 가입하기를 눌러 주세요.',
  csrfUnavailable: '보안 확인 정보를 저장하지 못했습니다. 브라우저 쿠키 설정을 확인한 뒤 다시 시도해 주세요.',
  unknownResult: '가입 결과를 확인하지 못했습니다. 이미 가입됐을 수 있으니 로그인을 시도하거나 잠시 뒤 다시 확인해 주세요.',
  common: '가입하지 못했습니다. 잠시 뒤 다시 시도해 주세요.',
  staleResult: '입력이 바뀌어 이전 확인 결과는 표시하지 않았습니다. 다시 가입하기를 눌러 주세요.',
  tooManyWithTime: (minutes: number) => `요청이 많습니다. 약 ${minutes}분 뒤 다시 시도해 주세요.`,
  tooMany: '요청이 많습니다. 잠시 뒤 다시 시도해 주세요.',
} as const;

/** 브라우저 setTimeout 최대 지연(2^31-1ms). 넘으면 즉시 실행되므로 마감 시각까지 나눠 기다린다. */
const MAX_TIMER_DELAY_MS = 2 ** 31 - 1;

/** 지금부터 seconds초 뒤의 마감 시각(ms). 이벤트 처리 중에만 부른다. */
function deadlineAfter(seconds: number): number {
  return Date.now() + seconds * 1000;
}

/** 마감 시각까지 남은 시간(ms). 0 이하면 지났다. */
function remainingUntil(deadline: number): number {
  return deadline - Date.now();
}

/**
 * Backend가 판정에 쓰는 값(정규화한 전송 값). 늦게 온 서버 오류가 아직 같은 입력에 대한 것인지 비교할 때 쓴다.
 * username은 ASCII 소문자화, 이메일은 strip + 소문자, 닉네임은 strip(SignupRequest). 비밀번호는 원문.
 */
function judgedValue(field: SignupField, input: SignupInput): string {
  switch (field) {
    case 'username':
      return normalizeUsername(input.username);
    case 'email':
      return javaStrip(input.email).toLowerCase();
    case 'nickname':
      return javaStrip(input.nickname);
    case 'password':
    case 'passwordConfirm':
      return input[field];
  }
}

/** 필드 오류가 의존하는 입력(문맥 단어는 아이디, 확인 불일치는 비밀번호에도 의존한다). */
const DEPENDS_ON: Record<SignupField, readonly SignupField[]> = {
  username: ['username'],
  email: ['email'],
  nickname: ['nickname'],
  password: ['password', 'username'],
  passwordConfirm: ['password', 'passwordConfirm'],
};

type Banner = { tone: 'error' | 'warning'; text: string; role: 'alert' | 'status' } | null;

const EMPTY: SignupInput = { username: '', email: '', nickname: '', password: '', passwordConfirm: '' };

function isSignupField(name: string): name is SignupField {
  return (SIGNUP_FIELDS as readonly string[]).includes(name);
}

function hasAnyError(...groups: SignupFieldErrors[]): boolean {
  return groups.some((errors) => SIGNUP_FIELDS.some((name) => errors[name] !== undefined));
}

export function SignupForm({ client, onSuccess }: { client: ApiClient; onSuccess: (result: SignupResult) => void }) {
  const [values, setValues] = useState<SignupInput>(EMPTY);
  const [clientErrors, setClientErrors] = useState<SignupFieldErrors>({});
  const [serverErrors, setServerErrors] = useState<SignupFieldErrors>({});
  /** 0이면 요약을 숨긴다. 실패할 때마다 늘려 새 alert 요소로 다시 알린다(FE-D028). */
  const [summaryKey, setSummaryKey] = useState(0);
  const [banner, setBanner] = useState<Banner>(null);
  const [submitting, setSubmitting] = useState(false);
  const [blocked, setBlocked] = useState(false);
  const [visible, setVisible] = useState<Record<'password' | 'passwordConfirm', boolean>>({ password: false, passwordConfirm: false });
  const [focusTarget, setFocusTarget] = useState<{ field: SignupField } | null>(null);

  // 이벤트 처리는 렌더 시점 클로저가 아니라 최신 값·오류(ref)를 기준으로 한다. 화면은 state로 그린다
  const valuesRef = useRef<SignupInput>(EMPTY);
  const clientErrorsRef = useRef<SignupFieldErrors>({});
  const serverErrorsRef = useRef<SignupFieldErrors>({});
  const submittingRef = useRef(false);
  const blockedUntilRef = useRef(0);
  const inputs = useRef<Partial<Record<SignupField, HTMLInputElement | null>>>({});
  const abortRef = useRef<AbortController | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      abortRef.current?.abort();
      if (timerRef.current !== null) clearTimeout(timerRef.current);
    },
    [],
  );

  // 실패 뒤 첫 오류 필드로 포커스를 옮긴다(FE-D028). 대상은 실패를 처리할 때 정한다
  useEffect(() => {
    if (focusTarget !== null) inputs.current[focusTarget.field]?.focus();
  }, [focusTarget]);

  const errorOf = (name: SignupField) => serverErrors[name] ?? clientErrors[name];

  function announceFieldErrors(errors: SignupFieldErrors) {
    const first = SIGNUP_FIELDS.find((name) => errors[name] !== undefined);
    if (first === undefined) return;
    setSummaryKey((n) => n + 1);
    setFocusTarget({ field: first });
  }

  /** 오류가 모두 사라지면 요약을 끈다. 다음 제출 실패 때만 다시 켜서, 입력·blur만으로는 다시 알리지 않는다(FE-D028). */
  function updateErrors(nextClient: SignupFieldErrors, nextServer: SignupFieldErrors) {
    clientErrorsRef.current = nextClient;
    serverErrorsRef.current = nextServer;
    setClientErrors(nextClient);
    setServerErrors(nextServer);
    if (!hasAnyError(nextClient, nextServer)) setSummaryKey(0);
  }

  function handleChange(name: SignupField, value: string) {
    // 최신 입력(valuesRef) 위에 쌓는다. 렌더 시점 값에 쌓으면 이어진 입력이 덮일 수 있다
    const next = { ...valuesRef.current, [name]: value };
    valuesRef.current = next;
    setValues(next);
    const nextServer: SignupFieldErrors = { ...serverErrorsRef.current };
    delete nextServer[name];
    // 오류가 표시된 필드만 입력할 때마다 다시 검사한다. 다른 필드에 따라 바뀌는 비밀번호·확인도 함께 본다(FE-D022)
    const currentClient = clientErrorsRef.current;
    const nextClient: SignupFieldErrors = { ...currentClient };
    for (const shown of SIGNUP_FIELDS) {
      if (currentClient[shown] === undefined) continue;
      const message = validateField(shown, next);
      if (message === null) delete nextClient[shown];
      else nextClient[shown] = message;
    }
    updateErrors(nextClient, nextServer);
  }

  function handleBlur(name: SignupField) {
    const message = validateField(name, valuesRef.current);
    const nextClient: SignupFieldErrors = { ...clientErrorsRef.current };
    if (message === null) delete nextClient[name];
    else nextClient[name] = message;
    updateErrors(nextClient, serverErrorsRef.current);
  }

  /** 제출 뒤 바뀌지 않은 입력에 대한 서버 필드 오류만 적용한다(늦은 응답이 고친 값에 붙지 않게). */
  function applyServerFieldErrors(errors: SignupFieldErrors, submitted: SignupInput, dependsOn = DEPENDS_ON) {
    const current = valuesRef.current;
    const fresh: SignupFieldErrors = {};
    for (const field of SIGNUP_FIELDS) {
      const message = errors[field];
      if (message === undefined) continue;
      const unchanged = dependsOn[field].every((dep) => judgedValue(dep, current) === judgedValue(dep, submitted));
      if (unchanged) fresh[field] = message;
    }
    updateErrors(clientErrorsRef.current, fresh);
    if (!hasAnyError(fresh)) {
      // 모두 낡은 결과다. 필드 오류·포커스는 그대로 두고 실패 사실만 조용히 알린다(FE-D031, 재리뷰 Q1=a)
      setBanner({ tone: 'warning', text: TEXT.staleResult, role: 'status' });
      return;
    }
    announceFieldErrors(fresh);
  }

  function blockUntil(seconds: number) {
    blockedUntilRef.current = deadlineAfter(seconds);
    setBlocked(true);
    const check = () => {
      const remaining = remainingUntil(blockedUntilRef.current);
      if (remaining <= 0) {
        timerRef.current = null;
        setBlocked(false);
        return;
      }
      timerRef.current = setTimeout(check, Math.min(remaining, MAX_TIMER_DELAY_MS));
    };
    if (timerRef.current !== null) clearTimeout(timerRef.current);
    check();
  }

  function showFailure(failure: ApiFailure, submitted: SignupInput) {
    const presentation = presentApiFailure(failure);
    if (presentation === 'silent') return;
    if (presentation === 'csrf-retry') {
      setBanner({ tone: 'error', text: TEXT.csrfRetry, role: 'alert' });
      return;
    }
    if (failure.kind === 'csrf-unavailable') {
      setBanner({ tone: 'error', text: TEXT.csrfUnavailable, role: 'alert' });
      return;
    }
    if (failure.kind === 'network' || failure.kind === 'timeout' || failure.kind === 'invalid-response') {
      setBanner({ tone: 'error', text: TEXT.unknownResult, role: 'alert' });
      return;
    }
    if (failure.kind !== 'http') {
      setBanner({ tone: 'error', text: TEXT.common, role: 'alert' });
      return;
    }
    const code = failure.problem?.code;
    if (failure.status === 429) {
      const seconds = failure.retryAfterSeconds;
      if (seconds === undefined) {
        setBanner({ tone: 'warning', text: TEXT.tooMany, role: 'status' });
        return;
      }
      setBanner({ tone: 'warning', text: TEXT.tooManyWithTime(Math.ceil(seconds / 60)), role: 'status' });
      blockUntil(seconds);
      return;
    }
    if (code === 'MEMBER_PASSWORD_BREACHED') {
      // 유출 여부는 비밀번호에만 달려 있다
      applyServerFieldErrors({ password: TEXT.breached }, submitted, { ...DEPENDS_ON, password: ['password'] });
      return;
    }
    if (code === 'MEMBER_USERNAME_DUPLICATE' || code === 'MEMBER_EMAIL_DUPLICATE') {
      applyServerFieldErrors(
        code === 'MEMBER_USERNAME_DUPLICATE' ? { username: TEXT.usernameDuplicate } : { email: TEXT.emailDuplicate },
        submitted,
      );
      return;
    }
    if (failure.status === 400 && failure.problem !== null) {
      const mapped: SignupFieldErrors = {};
      for (const { field, message } of failure.problem.fieldErrors) {
        if (isSignupField(field) && mapped[field] === undefined) mapped[field] = message;
      }
      if (Object.keys(mapped).length > 0) {
        applyServerFieldErrors(mapped, submitted);
        return;
      }
    }
    if (failure.status === 503) {
      setBanner({ tone: 'error', text: TEXT.retryLater, role: 'alert' });
      return;
    }
    setBanner({ tone: 'error', text: TEXT.common, role: 'alert' });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // 버튼 상태와 별개로 막는다(동기 플래그, 서버가 정한 대기 시각)
    if (submittingRef.current || remainingUntil(blockedUntilRef.current) > 0) return;
    setBanner(null);
    const submitted = valuesRef.current;
    const errors = validateSignup(submitted);
    // 제출도 같은 경로로 오류를 바꿔, 오류가 없으면 요약 키를 끈다(다음 실패 때만 다시 알림)
    updateErrors(errors, {});
    if (Object.keys(errors).length > 0) {
      announceFieldErrors(errors);
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const result = await submitSignup(client, submitted, controller.signal);
      onSuccess(result);
    } catch (error) {
      if (controller.signal.aborted) return; // 화면을 떠났다
      if (isApiError(error)) showFailure(error.failure, submitted);
      else setBanner({ tone: 'error', text: TEXT.common, role: 'alert' });
    } finally {
      submittingRef.current = false;
      if (!controller.signal.aborted) setSubmitting(false);
    }
  }

  const loweredUsername = normalizeUsername(values.username);
  const usernameNotice =
    loweredUsername !== values.username ? `소문자로 바뀌어 가입됩니다: ${loweredUsername}` : undefined;

  function renderField(name: SignupField, type: string) {
    const id = `signup-${name}`;
    const error = errorOf(name);
    const hint = name === 'username' ? usernameNotice ?? HINTS.username : HINTS[name];
    const describedBy = [hint === undefined ? null : `${id}-hint`, error === undefined ? null : `${id}-error`].filter(Boolean).join(' ');
    const secret = name === 'password' || name === 'passwordConfirm';
    const shown = secret && visible[name];
    return (
      <div className={styles.field}>
        <label htmlFor={id} className={styles.label}>
          {LABELS[name]}
        </label>
        <div className={secret ? styles.inputRow : undefined}>
          <input
            id={id}
            name={name}
            ref={(element) => {
              inputs.current[name] = element;
            }}
            className={error === undefined ? styles.input : `${styles.input} ${styles.inputInvalid}`}
            type={secret ? (shown ? 'text' : 'password') : type}
            autoComplete={AUTOCOMPLETE[name]}
            value={values[name]}
            aria-invalid={error === undefined ? undefined : true}
            aria-describedby={describedBy === '' ? undefined : describedBy}
            onChange={(event) => handleChange(name, event.target.value)}
            onBlur={() => handleBlur(name)}
          />
          {secret && (
            <button
              type="button"
              className={styles.toggle}
              aria-pressed={shown}
              aria-controls={id}
              aria-label={`${LABELS[name]} 표시`}
              onClick={() => setVisible((current) => ({ ...current, [name]: !current[name] }))}
            >
              {shown ? '숨기기' : '표시'}
            </button>
          )}
        </div>
        {hint !== undefined && (
          <p id={`${id}-hint`} className={styles.hint}>
            {hint}
          </p>
        )}
        {error !== undefined && (
          <p id={`${id}-error`} className={styles.error}>
            <span aria-hidden="true">✕ </span>
            {error}
          </p>
        )}
      </div>
    );
  }

  const hasFieldError = SIGNUP_FIELDS.some((name) => errorOf(name) !== undefined);
  const buttonLabel = submitting ? '가입 중…' : blocked ? '잠시 후 다시 시도' : '가입하기';

  return (
    <form className={styles.form} noValidate onSubmit={handleSubmit} aria-busy={submitting}>
      {summaryKey > 0 && hasFieldError && (
        // 개수 없는 고정 문구다. 입력을 고쳐도 내용이 바뀌지 않아 다시 읽히지 않는다(남은 개수와 다른 정보를 주지 않음)
        <div key={summaryKey} role="alert" className={`${styles.banner} ${styles.bannerError}`}>
          {TEXT.summary}
        </div>
      )}
      {banner !== null && (
        <div role={banner.role} className={`${styles.banner} ${banner.tone === 'error' ? styles.bannerError : styles.bannerWarning}`}>
          {banner.text}
        </div>
      )}
      {renderField('username', 'text')}
      {renderField('email', 'email')}
      {renderField('nickname', 'text')}
      {renderField('password', 'password')}
      {renderField('passwordConfirm', 'password')}
      <button type="submit" className={styles.submit} disabled={submitting || blocked}>
        {buttonLabel}
      </button>
    </form>
  );
}
