import type { ApiClient } from '../../shared/api/apiClient';
import { parseOtpRequired, type OtpRequired } from '../otp/otpApi';

export interface LoginInput {
  username: string;
  password: string;
}

/**
 * 로그인 1차 인증(`POST /api/v1/auth/login`). 성공은 로그인 완료가 아니라 OTP 확인 대기다(MB-D011).
 * username·비밀번호는 입력 그대로 보낸다: 정규화는 서버가 하고, 길이·형식은 검사하지 않으며 Trim하지 않는다
 * (FE-D037, FE-D033 (3)). 실패해도 다시 보내지 않는다.
 */
export function submitLogin(client: ApiClient, input: LoginInput, signal?: AbortSignal): Promise<OtpRequired> {
  return client.request(
    {
      method: 'post',
      path: '/v1/auth/login',
      authScope: 'none',
      data: { username: input.username, password: input.password },
      ...(signal === undefined ? {} : { signal }),
    },
    parseOtpRequired,
  );
}
