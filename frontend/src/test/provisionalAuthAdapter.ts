import type { ApiClient } from '../shared/api/apiClient';
import type { AuthAdapter } from '../features/auth/authAdapter';

/**
 * 테스트 전용 가안 어댑터(FE-D005). Backend 인증 계약(FE-U02) 전이라 경로·응답 모양은 확정이 아니다.
 * 운영 빌드에 이 표지 문자열이 있으면 빌드 검사가 실패한다(FE-T015).
 */
export const PROVISIONAL_MARKER = 'PROVISIONAL-AUTH-MOCK';
/** 가안 경로. 실제 현재 회원 조회 API는 Backend 협의 안건이다. */
export const PROVISIONAL_ME_PATH = '/v1/provisional/me';

export interface ProvisionalPrincipal {
  marker: typeof PROVISIONAL_MARKER;
  memberId: string;
}

export function createProvisionalAuthAdapter(client: ApiClient): AuthAdapter<ProvisionalPrincipal> {
  return {
    async initialize({ signal }) {
      const body = await client.request(
        { method: 'get', path: PROVISIONAL_ME_PATH, authScope: 'initialization', signal },
        (data) => {
          if (typeof data === 'object' && data !== null && typeof (data as { memberId?: unknown }).memberId === 'string') {
            return data as { memberId: string };
          }
          throw new Error('가안 응답 형식 오류');
        },
      );
      return { authenticated: true, principal: { marker: PROVISIONAL_MARKER, memberId: body.memberId } };
    },
  };
}
