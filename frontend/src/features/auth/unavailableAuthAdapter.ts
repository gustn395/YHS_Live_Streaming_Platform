import type { AuthAdapter } from './authAdapter';

/**
 * 운영·일반 개발용 미연결 어댑터(FE-D005). Backend 인증 계약 전이므로 네트워크를 쓰지 않고 항상 미인증이다.
 * 인증 성공을 돌려주는 경로가 없고, 환경변수로 다른 어댑터를 고르는 분기도 두지 않는다.
 */
export const unavailableAuthAdapter: AuthAdapter<never> = {
  initialize: () => Promise.resolve({ authenticated: false }),
};
