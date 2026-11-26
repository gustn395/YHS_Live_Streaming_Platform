export interface CombinedSignal {
  signal: AbortSignal | undefined;
  /** 수동 연결로 만든 리스너를 정리한다. 요청이 끝나면 반드시 호출한다. */
  release: () => void;
}

/**
 * 여러 AbortSignal 중 하나라도 abort되면 abort되는 signal을 만든다(FE-D011 호출자 signal 결합).
 * `AbortSignal.any`가 있으면 쓰고, 없으면 수동으로 연결한다.
 */
export function combineSignals(signals: ReadonlyArray<AbortSignal | undefined>): CombinedSignal {
  const present = signals.filter((s): s is AbortSignal => s !== undefined);
  if (present.length === 0) return { signal: undefined, release: () => {} };
  if (present.length === 1) return { signal: present[0], release: () => {} };
  if (typeof AbortSignal.any === 'function') return { signal: AbortSignal.any(present), release: () => {} };

  const controller = new AbortController();
  const listeners: Array<[AbortSignal, () => void]> = [];
  const release = () => {
    for (const [s, l] of listeners) s.removeEventListener('abort', l);
    listeners.length = 0;
  };
  for (const s of present) {
    if (s.aborted) {
      controller.abort(s.reason);
      release();
      break;
    }
    const listener = () => {
      controller.abort(s.reason);
      release();
    };
    s.addEventListener('abort', listener, { once: true });
    listeners.push([s, listener]);
  }
  return { signal: controller.signal, release };
}
