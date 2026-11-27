import type { ApiFailure } from './apiError';
import { presentApiFailure } from './errorPresentation';

/**
 * 폼 화면(로그인·OTP)이 공통으로 쓰는 실패 분류. 화면은 `rejected`만 자기 계약(상태·코드)으로 해석한다.
 * - silent: 취소. 표시하지 않음
 * - csrf-retry: 403 AUTH_CSRF_INVALID(FE-D021)
 * - csrf-unavailable: CSRF 쿠키를 저장하지 못함(FE-D031)
 * - transport: network·timeout·2xx 형식 오류. 결과를 모른다
 * - too-many: 429(`Retry-After` 초가 있으면 함께, FE-D025)
 * - unavailable: 503 일시 장애
 * - rejected: 그 밖의 HTTP 오류(403 포함, 권한 없음 화면으로 보내지 않는다, FE-D040 (2))
 */
export type FormFailure =
  | { kind: 'silent' }
  | { kind: 'csrf-retry' }
  | { kind: 'csrf-unavailable' }
  | { kind: 'transport' }
  | { kind: 'too-many'; retryAfterSeconds?: number }
  | { kind: 'unavailable' }
  | { kind: 'rejected'; status: number; code?: string };

export function classifyFormFailure(failure: ApiFailure): FormFailure {
  const presentation = presentApiFailure(failure);
  if (presentation === 'silent') return { kind: 'silent' };
  if (presentation === 'csrf-retry') return { kind: 'csrf-retry' };
  if (failure.kind === 'csrf-unavailable') return { kind: 'csrf-unavailable' };
  if (failure.kind !== 'http') return { kind: 'transport' };
  if (failure.status === 429) {
    return failure.retryAfterSeconds === undefined
      ? { kind: 'too-many' }
      : { kind: 'too-many', retryAfterSeconds: failure.retryAfterSeconds };
  }
  if (failure.status === 503) return { kind: 'unavailable' };
  const code = failure.problem?.code;
  return code === undefined ? { kind: 'rejected', status: failure.status } : { kind: 'rejected', status: failure.status, code };
}
