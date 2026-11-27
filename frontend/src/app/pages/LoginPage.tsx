import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { safeRedirect } from '../../features/auth/safeRedirect';
import { useAuth } from '../../features/auth/useAuth';
import { googleLoginErrorMessage } from '../../features/login/googleLoginError';
import { LoginForm } from '../../features/login/LoginForm';
import { RESEND_INTERVAL_SECONDS } from '../../features/otp/otpApi';
import { isOtpRestartReason, type OtpRestartReason } from '../../features/otp/otpText';
import { deadlineAfter } from '../../shared/time/deadline';
import formStyles from '../../shared/ui/AuthForm.module.css';
import { FormBanner } from '../../shared/ui/FormBanner';
import { errorBanner } from '../../shared/ui/formFeedback';
import { apiClient } from '../apiClient';
import styles from './AuthLayout.module.css';
import type { LoginPageState, OtpPageState } from './loginFlowState';
import { GOOGLE_LOGIN_START, LOGIN_PAGE_OR, LOGIN_PAGE_TEXT } from './loginPageText';

/** history state는 조작될 수 있으므로 정해진 사유만 받는다. */
function noticeOf(raw: unknown): OtpRestartReason | null {
  if (typeof raw !== 'object' || raw === null) return null;
  const notice = (raw as Partial<Record<keyof LoginPageState, unknown>>).notice;
  return isOtpRestartReason(notice) ? notice : null;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { state: auth } = useAuth();
  // 처음 받은 error 쿼리·다시 시작 사유로 문구를 정해 두고, 주소·기록에서는 지운다(새로고침·공유로 다시 뜨지 않게, FE-D038, FE-D042 (2))
  const [googleError] = useState(() => googleLoginErrorMessage(searchParams.get('error')));
  const [notice] = useState(() => noticeOf(location.state));
  const fromParam = searchParams.get('from');
  // RequireAuth가 붙인 from은 안전한 내부 경로일 때만 따른다(FE-D012). 아니면 safeRedirect가 '/'를 돌려준다
  const from = fromParam === null ? null : safeRedirect(fromParam, window.location.origin);

  useEffect(() => {
    const hasError = searchParams.has('error');
    if (!hasError && noticeOf(location.state) === null) return;
    const next = new URLSearchParams(searchParams);
    next.delete('error');
    const search = next.toString();
    // error만 지우고 from은 남긴다. 다시 시작 사유는 history state째 지운다
    void navigate({ pathname: location.pathname, search: search === '' ? '' : `?${search}` }, { replace: true, state: null });
  }, [location.pathname, location.state, navigate, searchParams]);

  return (
    <main className={styles.page}>
      <p className={styles.brand}>YHS Live Streaming</p>
      <div className={styles.card}>
        <h1 className={styles.title}>로그인</h1>
        {auth.status === 'unauthenticated' && auth.reason === 'sessionLost' && (
          <p role="status">{LOGIN_PAGE_TEXT.sessionLost}</p>
        )}
        <FormBanner banner={notice === null ? null : errorBanner(LOGIN_PAGE_TEXT[notice])} bannerKey={0} />
        <FormBanner banner={googleError === null ? null : errorBanner(googleError)} bannerKey={0} />
        <LoginForm
          client={apiClient}
          ready={auth.status !== 'initializing'}
          onSuccess={(result) => {
            const state: OtpPageState = {
              otpExpiresAt: result.otpExpiresAt,
              resendAvailableAt: deadlineAfter(RESEND_INTERVAL_SECONDS),
              from,
            };
            void navigate('/login/otp', { state });
          }}
        />
        <p className={styles.footer}>{LOGIN_PAGE_OR}</p>
        <a className={formStyles.secondary} href={GOOGLE_LOGIN_START}>
          Google로 로그인
        </a>
        <p className={styles.footer}>
          계정이 없나요? <Link to="/signup">회원가입</Link>
        </p>
      </div>
    </main>
  );
}
