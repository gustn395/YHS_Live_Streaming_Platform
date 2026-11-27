import { Link, Navigate, useLocation } from 'react-router';
import styles from './AuthLayout.module.css';

function cameFromSignup(state: unknown): boolean {
  return typeof state === 'object' && state !== null && (state as { signupCompleted?: unknown }).signupCompleted === true;
}

/** 가입 완료 안내(FE-D026). 가입 성공 때 넘긴 state가 없으면 가입 화면으로 보낸다. */
export function SignupCompletePage() {
  const location = useLocation();
  if (!cameFromSignup(location.state)) return <Navigate to="/signup" replace />;
  return (
    <main className={styles.page}>
      <p className={styles.brand}>YHS Live Streaming</p>
      <div className={styles.card}>
        <h1 className={styles.title}>가입 신청이 완료됐습니다</h1>
        <p>24시간 안에 첫 로그인을 하고 이메일로 받은 확인 번호를 입력해야 계정이 활성화됩니다.</p>
        <Link to="/login" className={styles.primaryLink}>
          로그인하러 가기
        </Link>
        <p className={styles.footer}>
          <Link to="/">처음으로</Link>
        </p>
      </div>
    </main>
  );
}
