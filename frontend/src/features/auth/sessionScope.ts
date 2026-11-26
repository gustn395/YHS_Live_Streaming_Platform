import type { AuthBridge } from '../../shared/api/apiClient';

export interface SessionScope extends AuthBridge {
  isAuthenticated(): boolean;
  /** 초기화를 시작한 세대가 지금도 현재 세대일 때만 인증 성공을 반영한다. 반영하면 true. */
  markAuthenticated(startEpoch: number): boolean;
  /** 세션 상실 리스너는 하나만 구독한다. 돌려준 함수로 해제한다. */
  subscribeSessionLost(listener: (epoch: number) => void): () => void;
}

/**
 * 인증 세대와 세대별 취소를 React 렌더링과 별개로 동기적으로 관리한다(FE-D011).
 * 세대는 새 인증 성공·세션 상실에서만 올린다. 세대가 오르면 이전 세대의 session 요청만 abort한다.
 */
export function createSessionScope(): SessionScope {
  let epoch = 0;
  let authenticated = false;
  let controller = new AbortController();
  let listener: ((epoch: number) => void) | null = null;

  const advance = (nextAuthenticated: boolean) => {
    controller.abort();
    controller = new AbortController();
    epoch += 1;
    authenticated = nextAuthenticated;
  };

  return {
    currentEpoch: () => epoch,
    isAuthenticated: () => authenticated,

    sessionSignal(requested: number): AbortSignal {
      return requested === epoch ? controller.signal : AbortSignal.abort();
    },

    markAuthenticated(startEpoch: number): boolean {
      if (startEpoch !== epoch) return false;
      advance(true);
      return true;
    },

    reportSessionLost(lostEpoch: number): void {
      // 동시 401이 여러 번 와도 같은 세대에서 한 번만 전이한다(중복 로그아웃·이동·알림 방지).
      if (!authenticated || lostEpoch !== epoch) return;
      advance(false);
      listener?.(lostEpoch);
    },

    subscribeSessionLost(next: (epoch: number) => void): () => void {
      if (listener !== null) throw new Error('세션 상실 리스너가 이미 있다');
      listener = next;
      return () => {
        if (listener === next) listener = null;
      };
    },
  };
}
