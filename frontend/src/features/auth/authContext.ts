import { createContext } from 'react';
import type { AuthState } from './authState';

export interface AuthContextValue {
  state: AuthState<unknown>;
  /** initError가 있을 때만 초기화를 다시 시작한다. */
  retry: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
