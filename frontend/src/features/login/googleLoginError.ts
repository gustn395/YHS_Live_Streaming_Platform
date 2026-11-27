import { COMMON_TEXT } from '../../shared/ui/commonText';

/**
 * Google 로그인 실패 코드(member MB-D024, Backend가 `/login?error=`로 붙인다)의 고정 문구(FE-D038).
 * 쿼리는 누구나 만들 수 있으므로 받은 값을 화면에 그대로 출력하지 않고, 정해진 코드만 문구로 바꾼다.
 */
export const GOOGLE_LOGIN_ERROR_TEXT = {
  oauth_failed: 'Google 로그인에 실패했습니다. 다시 시도해 주세요.',
  oauth_email_unverified: 'Google 계정의 이메일이 확인되지 않았습니다. Google에서 이메일을 확인한 뒤 다시 시도해 주세요.',
  account_disabled: COMMON_TEXT.accountDisabled,
  // 302 이동에는 Retry-After가 없어 대기 시간을 말하지 않는다
  rate_limited: COMMON_TEXT.tooMany,
  otp_delivery_failed: '인증 번호 메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.',
  temporarily_unavailable: '일시적으로 Google 로그인을 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요.',
  unknown: 'Google 로그인을 마치지 못했습니다. 다시 시도해 주세요.',
} as const;

type GoogleLoginErrorCode = Exclude<keyof typeof GOOGLE_LOGIN_ERROR_TEXT, 'unknown'>;

function isKnownCode(value: string): value is GoogleLoginErrorCode {
  return value !== 'unknown' && Object.hasOwn(GOOGLE_LOGIN_ERROR_TEXT, value);
}

/** `error` 쿼리 값의 안내 문구. 값이 없으면 null, 알 수 없는 값은 일반 문구다. */
export function googleLoginErrorMessage(value: string | null): string | null {
  if (value === null) return null;
  return isKnownCode(value) ? GOOGLE_LOGIN_ERROR_TEXT[value] : GOOGLE_LOGIN_ERROR_TEXT.unknown;
}
