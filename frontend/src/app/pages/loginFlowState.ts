import type { OtpRestartReason } from '../../features/otp/otpText';

/**
 * 로그인 화면이 OTP 화면으로 넘기는 history state(FE-D035, FE-D036, FE-D041). 보안 판정이 아니라 화면 흐름이다.
 * Google 진입(Backend 302)은 state가 없다.
 */
export interface OtpPageState {
  otpExpiresAt: number;
  resendAvailableAt: number;
  from: string | null;
}

/** OTP 화면이 다시 시작할 때 로그인 화면으로 넘기는 history state(FE-D037 (3)). 로그인 화면은 한 번 보인 뒤 지운다(FE-D042 (2)). */
export interface LoginPageState {
  notice: OtpRestartReason;
}

/** 로그인 화면 주소. 원래 가려던 경로(from)가 있으면 쿼리로 유지한다. */
export function loginPath(from: string | null): string {
  return from === null ? '/login' : `/login?${new URLSearchParams({ from }).toString()}`;
}
