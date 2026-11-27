import { COMMON_TEXT } from '../../shared/ui/commonText';

/** 로그인 폼 고정 문구(FE-D037). 서버의 detail·title은 표시하지 않는다. */
export const LOGIN_TEXT = {
  empty: '아이디와 비밀번호를 입력해 주세요.',
  // 형식이 틀린 입력(400)도 같은 문구다: 서버도 계정 존재를 드러내지 않는다(MB-D011)
  loginFailed: '아이디 또는 비밀번호가 올바르지 않습니다.',
  disabled: COMMON_TEXT.accountDisabled,
  csrfRetry: '보안 확인이 만료됐습니다. 다시 로그인을 눌러 주세요.',
  csrfUnavailable: COMMON_TEXT.csrfUnavailable,
  retryLater: '일시적으로 로그인할 수 없습니다. 잠시 뒤 다시 시도해 주세요.',
  common: '로그인하지 못했습니다. 잠시 뒤 다시 시도해 주세요.',
  checking: '로그인 상태를 확인하는 중입니다.',
  tooMany: COMMON_TEXT.tooMany,
  tooManyWithTime: COMMON_TEXT.tooManyWithTime,
} as const;
