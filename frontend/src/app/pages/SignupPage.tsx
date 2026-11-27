import { Link, useNavigate } from 'react-router';
import { SignupForm } from '../../features/signup/SignupForm';
import { apiClient } from '../apiClient';
import styles from './AuthLayout.module.css';

/** 완료 화면이 "가입을 마치고 왔는지"를 아는 history state(FE-D026). 보안 판정이 아니라 화면 흐름이다. */
const SIGNUP_COMPLETED_STATE = { signupCompleted: true } as const;

export function SignupPage() {
  const navigate = useNavigate();
  return (
    <main className={styles.page}>
      <p className={styles.brand}>YHS Live Streaming</p>
      <div className={styles.card}>
        <h1 className={styles.title}>회원가입</h1>
        <SignupForm
          client={apiClient}
          onSuccess={() => {
            // replace: 뒤로 가기로 입력값이 든 가입 폼에 돌아가지 않는다. memberId는 넘기지 않는다
            void navigate('/signup/complete', { replace: true, state: SIGNUP_COMPLETED_STATE });
          }}
        />
        <p className={styles.footer}>
          이미 계정이 있나요? <Link to="/login">로그인</Link>
        </p>
      </div>
    </main>
  );
}
