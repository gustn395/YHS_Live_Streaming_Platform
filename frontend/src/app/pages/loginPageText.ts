import type { OtpRestartReason } from '../../features/otp/otpText';
import { COMMON_TEXT } from '../../shared/ui/commonText';

/** Google 로그인 시작(FE-D038). Backend 필터가 처리하는 문서 이동이라 Router Link가 아니라 a 요소로 간다. */
export const GOOGLE_LOGIN_START = '/api/v1/auth/oauth2/authorization/google';

/** 로그인 화면 안내: OTP 화면에서 다시 시작하게 된 사유(FE-D037 (3))와 세션 상실. */
export const LOGIN_PAGE_TEXT: Record<OtpRestartReason | 'sessionLost', string> = {
  challengeInvalid: '인증 시간이 지났거나 인증을 확인할 수 없습니다. 처음부터 다시 로그인해 주세요.',
  loginFailed: '로그인을 마치지 못했습니다. 처음부터 다시 로그인해 주세요.',
  disabled: COMMON_TEXT.accountDisabled,
  sessionLost: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
};

/** 로그인 폼과 Google 버튼 사이 구분 문구. */
export const LOGIN_PAGE_OR = '또는';
