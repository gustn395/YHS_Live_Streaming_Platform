import { createContext } from 'react';
import type { AuthState } from './authState';

export interface AuthContextValue {
  state: AuthState<unknown>;
  /** initError가 있을 때만 초기화를 다시 시작한다. */
  retry: () => void;
  /**
   * 로그인(OTP 확인) 성공을 반영한다(FE-D034). Reducer와 세대 관리(sessionScope)를 같은 세대로 함께 올리고, 이전 세대의
   * session 요청은 취소된다. 초기화가 끝나기 전이면 반영하지 않고 false를 돌려준다.
   */
  completeLogin: (principal: unknown) => boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
