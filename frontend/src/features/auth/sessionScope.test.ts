import { describe, expect, it, vi } from 'vitest';
import { createSessionScope } from './sessionScope';

function scopeWithListener() {
  const scope = createSessionScope();
  const onLost = vi.fn();
  const unsubscribe = scope.subscribeSessionLost(onLost);
  return { scope, onLost, unsubscribe };
}

// FE-T008·FE-T011: 세대 판정과 중복 방지는 React 렌더링과 별개로 동기적으로 한다(FE-D011)
describe('sessionScope', () => {
  it('처음은 세대 0, 미인증', () => {
    const scope = createSessionScope();
    expect(scope.currentEpoch()).toBe(0);
    expect(scope.isAuthenticated()).toBe(false);
  });

  it('인증 성공은 세대를 올리고 이전 세대 signal을 abort한다', () => {
    const scope = createSessionScope();
    const before = scope.sessionSignal(0);
    expect(scope.markAuthenticated(0)).toBe(true);
    expect(scope.currentEpoch()).toBe(1);
    expect(before.aborted).toBe(true);
    expect(scope.sessionSignal(1).aborted).toBe(false);
  });

  it('시작 세대가 현재 세대와 다르면 인증 성공을 반영하지 않는다', () => {
    const scope = createSessionScope();
    scope.markAuthenticated(0);
    expect(scope.markAuthenticated(0)).toBe(false);
    expect(scope.currentEpoch()).toBe(1);
  });

  it('지난 세대의 signal은 이미 abort된 상태로 준다', () => {
    const scope = createSessionScope();
    scope.markAuthenticated(0);
    expect(scope.sessionSignal(0).aborted).toBe(true);
  });

  it('같은 세대의 세션 상실 보고가 여러 번 와도 한 번만 알린다', () => {
    const { scope, onLost } = scopeWithListener();
    scope.markAuthenticated(0);
    const signal = scope.sessionSignal(1);
    scope.reportSessionLost(1);
    scope.reportSessionLost(1);
    scope.reportSessionLost(1);
    expect(onLost).toHaveBeenCalledTimes(1);
    expect(onLost).toHaveBeenCalledWith(1);
    expect(scope.currentEpoch()).toBe(2);
    expect(scope.isAuthenticated()).toBe(false);
    expect(signal.aborted).toBe(true);
  });

  it('미인증 상태나 다른 세대의 보고는 무시한다', () => {
    const { scope, onLost } = scopeWithListener();
    scope.reportSessionLost(0);
    scope.markAuthenticated(0);
    scope.reportSessionLost(0);
    scope.reportSessionLost(5);
    expect(onLost).not.toHaveBeenCalled();
    expect(scope.currentEpoch()).toBe(1);
  });

  it('구독을 해제하면 알리지 않지만 세대 전이는 그대로 한다', () => {
    const { scope, onLost, unsubscribe } = scopeWithListener();
    scope.markAuthenticated(0);
    unsubscribe();
    scope.reportSessionLost(1);
    expect(onLost).not.toHaveBeenCalled();
    expect(scope.currentEpoch()).toBe(2);
  });

  it('리스너는 하나만 구독한다(StrictMode 재구독은 해제 뒤에만)', () => {
    const { scope, unsubscribe } = scopeWithListener();
    expect(() => scope.subscribeSessionLost(vi.fn())).toThrow();
    unsubscribe();
    expect(() => scope.subscribeSessionLost(vi.fn())).not.toThrow();
  });
});
