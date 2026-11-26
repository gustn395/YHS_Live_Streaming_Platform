import type { InitResult } from './authState';

/**
 * 인증 초기화를 Backend 계약과 분리하는 인터페이스(FE-D005). 실제 구현 교체는 FE-U02.
 * 실패는 ApiError로 reject한다. 분류는 classifyInitFailure가 한다.
 */
export interface AuthAdapter<P> {
  initialize(context: { signal: AbortSignal }): Promise<InitResult<P>>;
}
