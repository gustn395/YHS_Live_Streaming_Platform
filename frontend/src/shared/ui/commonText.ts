/** 여러 화면이 같은 뜻으로 쓰는 고정 문구(로그인·OTP·Google 오류). 서버의 detail·title은 표시하지 않는다. */
export const COMMON_TEXT = {
  accountDisabled: '사용할 수 없는 계정입니다.',
  csrfUnavailable: '보안 확인 정보를 저장하지 못했습니다. 브라우저 쿠키 설정을 확인한 뒤 다시 시도해 주세요.',
  tooMany: '요청이 많습니다. 잠시 뒤 다시 시도해 주세요.',
  tooManyWithTime: (minutes: number) => `요청이 많습니다. 약 ${minutes}분 뒤 다시 시도해 주세요.`,
} as const;
