import { Link, useLocation, useNavigate } from 'react-router';
import { safeRedirect } from '../../features/auth/safeRedirect';
import { useAuth } from '../../features/auth/useAuth';
import { OtpForm } from '../../features/otp/OtpForm';
import { OTP_TEXT } from '../../features/otp/otpText';
import { apiClient } from '../apiClient';
import styles from './AuthLayout.module.css';
import { loginPath, type LoginPageState, type OtpPageState } from './loginFlowState';

const DEFAULT_AFTER_LOGIN = '/app';

function finiteOrNull(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/** state는 조작될 수 있으므로 모양을 확인하고, from은 다시 안전한 내부 경로로 검사한다(FE-D012). */
function readState(raw: unknown): { otpExpiresAt: number | null; resendAvailableAt: number | null; from: string | null } {
  const state = typeof raw === 'object' && raw !== null ? (raw as Record<string, unknown>) : {};
  const from = typeof state.from === 'string' ? safeRedirect(state.from, window.location.origin) : null;
  return { otpExpiresAt: finiteOrNull(state.otpExpiresAt), resendAvailableAt: finiteOrNull(state.resendAvailableAt), from };
}

export function OtpPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { state: auth, completeLogin } = useAuth();
  const { otpExpiresAt, resendAvailableAt, from } = readState(location.state);

  return (
    <main className={styles.page}>
      <p className={styles.brand}>YHS Live Streaming</p>
      <div className={styles.card}>
        <h1 className={styles.title}>인증 번호 확인</h1>
        <p>{OTP_TEXT.pageLead}</p>
        <OtpForm
          client={apiClient}
          ready={auth.status !== 'initializing'}
          otpExpiresAt={otpExpiresAt}
          resendAvailableAt={resendAvailableAt}
          onVerified={(principal) => {
            // 반영하지 못했으면 보호 화면으로 가지 않는다. 안내는 폼이 한다(FE-D042 (1))
            if (!completeLogin(principal)) return false;
            // replace: 뒤로 가기로 OTP 화면에 돌아가지 않는다(FE-D036)
            void navigate(from ?? DEFAULT_AFTER_LOGIN, { replace: true });
            return true;
          }}
          onResent={(result, availableAt) => {
            // 화면 기록도 새 값으로 바꿔, 같은 기록으로 돌아와도 이전 만료 시각을 보이지 않게 한다
            const next: OtpPageState = { otpExpiresAt: result.otpExpiresAt, resendAvailableAt: availableAt, from };
            void navigate(location.pathname, { replace: true, state: next });
          }}
          onRestart={(reason) => {
            const next: LoginPageState = { notice: reason };
            void navigate(loginPath(from), { replace: true, state: next });
          }}
        />
        <p className={styles.footer}>
          {/* 다시 시작과 같이 원래 가려던 경로를 유지한다 */}
          <Link to={loginPath(from)}>{OTP_TEXT.restartLink}</Link>
        </p>
      </div>
    </main>
  );
}
