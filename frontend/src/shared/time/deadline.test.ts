import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { deadlineAfter, MAX_TIMER_DELAY_MS, remainingUntil, retryMinutes, waitUntil } from './deadline';

// FE-T037(공용 모듈): 마감 시각 기준 대기(FE-D040 (3)). 로그인·OTP·회원가입 화면이 함께 쓴다
describe('deadline', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-27T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('deadlineAfter는 지금부터 초 뒤의 벽시계 시각(ms)이다', () => {
    expect(deadlineAfter(60)).toBe(Date.parse('2026-11-27T10:01:00Z'));
  });

  it('retryMinutes는 초를 분으로 올림한다', () => {
    expect([1, 60, 61, 600, 601, 3600].map(retryMinutes)).toEqual([1, 1, 2, 10, 11, 60]);
  });

  it('remainingUntil은 남은 ms이고 지나면 0 이하다', () => {
    const deadline = deadlineAfter(2);
    expect(remainingUntil(deadline)).toBe(2000);
    vi.advanceTimersByTime(2500);
    expect(remainingUntil(deadline)).toBe(-500);
  });

  it('waitUntil은 마감 시각에 한 번만 끝을 알린다', () => {
    const done = vi.fn();
    waitUntil(deadlineAfter(3), done);
    vi.advanceTimersByTime(2999);
    expect(done).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(done).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(10_000);
    expect(done).toHaveBeenCalledTimes(1);
  });

  it('이미 지난 마감 시각이면 바로 끝을 알린다', () => {
    const done = vi.fn();
    waitUntil(Date.now() - 1, done);
    expect(done).toHaveBeenCalledTimes(1);
  });

  it('브라우저 최대 지연(2^31-1ms)보다 긴 대기도 마감 전에 끝내지 않는다(나눠 기다림)', () => {
    const done = vi.fn();
    const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
    waitUntil(Date.now() + MAX_TIMER_DELAY_MS + 5000, done);
    expect(setTimeoutSpy.mock.calls.every(([, ms]) => (ms ?? 0) <= MAX_TIMER_DELAY_MS)).toBe(true);
    vi.advanceTimersByTime(MAX_TIMER_DELAY_MS);
    expect(done).not.toHaveBeenCalled();
    vi.advanceTimersByTime(5000);
    expect(done).toHaveBeenCalledTimes(1);
  });

  it('돌려준 함수로 취소하면 알리지 않는다', () => {
    const done = vi.fn();
    const cancel = waitUntil(deadlineAfter(3), done);
    cancel();
    vi.advanceTimersByTime(5000);
    expect(done).not.toHaveBeenCalled();
  });

  it('tick 간격을 주면 그 간격마다 남은 시간을 알린다', () => {
    const ticks: number[] = [];
    const done = vi.fn();
    waitUntil(deadlineAfter(3), done, { tickMs: 1000, onTick: (remaining) => ticks.push(remaining) });
    vi.advanceTimersByTime(3000);
    expect(ticks).toEqual([2000, 1000]);
    expect(done).toHaveBeenCalledTimes(1);
  });
});
