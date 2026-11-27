import { COMMON_TEXT } from '../../shared/ui/commonText';

/** 로그인 화면으로 돌아가 처음부터 다시 해야 하는 이유(FE-D037 (3)). 목록의 원본이며 타입과 검사는 여기서 만든다. */
export const OTP_RESTART_REASONS = ['challengeInvalid', 'loginFailed', 'disabled'] as const;
export type OtpRestartReason = (typeof OTP_RESTART_REASONS)[number];

export function isOtpRestartReason(value: unknown): value is OtpRestartReason {
  return typeof value === 'string' && (OTP_RESTART_REASONS as readonly string[]).includes(value);
}

function clock(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

/** OTP 화면 고정 문구(FE-D037). 서버의 detail·title은 표시하지 않고, 남은 시도 횟수도 말하지 않는다. */
export const OTP_TEXT = {
  pageLead: '이메일로 받은 4자리 인증 번호를 입력해 주세요.',
  inputHint: '메일로 받은 4자리 숫자를 입력해 주세요.',
  mismatch: '인증 번호가 일치하지 않습니다. 다시 입력해 주세요.',
  expired: '인증 번호가 만료됐습니다. 새 인증 번호를 받아 주세요.',
  expiredHint: '인증 번호 유효 시간이 지났습니다. 새 인증 번호를 받아 주세요.',
  resent: '새 인증 번호를 보냈습니다. 메일을 확인해 주세요.',
  csrfRetry: '보안 확인이 만료됐습니다. 다시 시도해 주세요.',
  csrfUnavailable: COMMON_TEXT.csrfUnavailable,
  retryLater: '일시적으로 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요.',
  common: '처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요.',
  // FE-D042 (1): 확인은 성공했지만 화면의 로그인 상태를 바꾸지 못했다
  loginNotApplied: '로그인 상태를 반영하지 못했습니다. 다시 로그인해 주세요.',
  restartLink: '처음부터 다시 로그인',
  tooMany: COMMON_TEXT.tooMany,
  tooManyWithTime: COMMON_TEXT.tooManyWithTime,
  remaining: (ms: number) => `남은 시간 ${clock(ms)}`,
  resend: '인증 번호 다시 받기',
  resendWait: (seconds: number) => `${seconds}초 뒤 다시 받기`,
} as const;
