import { useCallback, useEffect, useRef, useState } from 'react';
import { deadlineAfter, remainingUntil, waitUntil } from '../time/deadline';

/** 폼 위의 안내 한 줄. 오류는 `alert`, 대기·완료 안내는 `status`로 읽힌다(FE-D028). */
export type Banner = { tone: 'error' | 'warning'; text: string; role: 'alert' | 'status' };

export const errorBanner = (text: string): Banner => ({ tone: 'error', text, role: 'alert' });
export const statusBanner = (text: string): Banner => ({ tone: 'warning', text, role: 'status' });

/** 안내 상태. 보일 때마다 key를 올려 같은 문구도 새 요소로 다시 알린다(FE-D028). */
export function useBanner() {
  const [state, setState] = useState<{ banner: Banner | null; key: number }>({ banner: null, key: 0 });
  const show = useCallback((banner: Banner) => setState((s) => ({ banner, key: s.key + 1 })), []);
  const clear = useCallback(() => setState((s) => (s.banner === null ? s : { banner: null, key: s.key })), []);
  return { banner: state.banner, bannerKey: state.key, show, clear };
}

/**
 * 429 `Retry-After` 동안 한 동작을 막는다(FE-D025). 화면 상태(blocked)와 별개로 isBlocked()가 이벤트 처리에서
 * 동기적으로 막는다. 언마운트하면 대기를 정리한다. 최종 판단은 서버다.
 */
export function useRetryBlock() {
  const [blocked, setBlocked] = useState(false);
  const untilRef = useRef(0);
  const cancelRef = useRef<(() => void) | null>(null);

  useEffect(() => () => cancelRef.current?.(), []);

  const block = useCallback((seconds: number) => {
    untilRef.current = deadlineAfter(seconds);
    setBlocked(true);
    cancelRef.current?.();
    cancelRef.current = waitUntil(untilRef.current, () => {
      cancelRef.current = null;
      setBlocked(false);
    });
  }, []);

  const isBlocked = useCallback(() => remainingUntil(untilRef.current) > 0, []);

  return { blocked, block, isBlocked };
}
