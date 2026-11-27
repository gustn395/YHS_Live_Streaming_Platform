import type { ApiClient } from '../../shared/api/apiClient';
import { normalizeUsername, type SignupInput } from './signupValidation';

/** Backend SignupResponse. 가입 직후 계정은 PENDING이다(MB-D003). */
export interface SignupResult {
  memberId: number;
  accountStatus: 'PENDING';
}

/** 201 상태·본문 계약 검사(FE-D031). 201이 아니거나 본문이 어긋나면 던져서 공통 Client가 invalid-response로 바꾼다. */
function parseSignupResult(data: unknown, status: number): SignupResult {
  if (status !== 201) throw new Error('가입 응답 상태 오류');
  if (typeof data !== 'object' || data === null) throw new Error('가입 응답 형식 오류');
  const { memberId, accountStatus } = data as Record<string, unknown>;
  if (typeof memberId !== 'number' || !Number.isSafeInteger(memberId) || memberId < 1) throw new Error('memberId 형식 오류');
  if (accountStatus !== 'PENDING') throw new Error('accountStatus 형식 오류');
  return { memberId, accountStatus };
}

/**
 * 일반 회원가입(`POST /api/v1/auth/signup`). 본문은 Backend SignupRequest 그대로다. 비밀번호는 원문을 보내고,
 * username만 Backend와 같은 규칙으로 소문자로 바꾼다. 실패해도 다시 보내지 않는다(FE-D031).
 */
export function submitSignup(client: ApiClient, input: SignupInput, signal?: AbortSignal): Promise<SignupResult> {
  return client.request(
    {
      method: 'post',
      path: '/v1/auth/signup',
      authScope: 'none',
      data: {
        username: normalizeUsername(input.username),
        password: input.password,
        passwordConfirm: input.passwordConfirm,
        email: input.email,
        nickname: input.nickname,
      },
      ...(signal === undefined ? {} : { signal }),
    },
    parseSignupResult,
  );
}
