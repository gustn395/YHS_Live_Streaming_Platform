/**
 * 마감 시각 기준 대기(FE-D040 (3)). 로그인·OTP·회원가입 화면이 함께 쓴다.
 *
 * Date.now(벽시계)를 쓴다: performance.now는 플랫폼에 따라 절전 중 멈춰 서버 기준으로 풀린 뒤에도 버튼을 막을 수 있다.
 * 시스템 시각을 바꾸면 흔들리지만, 이 대기는 안내용이고 최종 판단은 서버다(FE-D025, FE-D032).
 */

/** 브라우저 setTimeout 최대 지연(2^31-1ms). 넘으면 즉시 실행되므로 마감 시각까지 나눠 기다린다. */
export const MAX_TIMER_DELAY_MS = 2 ** 31 - 1;

/** 지금부터 seconds초 뒤의 마감 시각(ms). 이벤트 처리 중에만 부른다(렌더 중에 부르지 않는다). */
export function deadlineAfter(seconds: number): number {
  return Date.now() + seconds * 1000;
}

/** `Retry-After` 초를 안내할 분으로 올림한다(FE-D025: 601초 → 약 11분). */
export function retryMinutes(seconds: number): number {
  return Math.ceil(seconds / 60);
}

/** 마감 시각까지 남은 시간(ms). 0 이하면 지났다. */
export function remainingUntil(deadline: number): number {
  return deadline - Date.now();
}

/**
 * 마감 시각이 지나면 onDone을 한 번 부른다. 이미 지났으면 바로 부른다. 대기는 브라우저 최대 지연 이하로 나눠
 * 마감 전에 끝나지 않는다. tickMs를 주면 그 간격마다 남은 시간으로 onTick을 부른다(마감 시각에는 onDone만).
 * 돌려준 함수로 취소한다.
 */
export function waitUntil(
  deadline: number,
  onDone: () => void,
  options?: { tickMs: number; onTick: (remainingMs: number) => void },
): () => void {
  let timer: ReturnType<typeof setTimeout> | null = null;
  const step = Math.min(options?.tickMs ?? MAX_TIMER_DELAY_MS, MAX_TIMER_DELAY_MS);
  let first = true;

  const check = () => {
    timer = null;
    const remaining = remainingUntil(deadline);
    if (remaining <= 0) {
      onDone();
      return;
    }
    if (!first) options?.onTick(remaining);
    first = false;
    timer = setTimeout(check, Math.min(remaining, step));
  };
  check();

  return () => {
    if (timer !== null) clearTimeout(timer);
    timer = null;
  };
}
