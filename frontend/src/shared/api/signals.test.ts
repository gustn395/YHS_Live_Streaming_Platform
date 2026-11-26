import { afterEach, describe, expect, it, vi } from 'vitest';
import { combineSignals } from './signals';

// FE-T011(일부): 호출자 signal 결합. AbortSignal.any가 없는 환경의 대체 경로도 검증한다.
describe.each([
  ['AbortSignal.any 사용', false],
  ['수동 연결 대체', true],
])('combineSignals — %s', (_name, removeAny) => {
  afterEach(() => vi.unstubAllGlobals());

  function setup() {
    if (removeAny) {
      const Original = AbortSignal;
      const Stub = Object.assign(Object.create(Original) as object, { any: undefined });
      vi.stubGlobal('AbortSignal', Stub);
    }
  }

  it('signal이 없으면 undefined', () => {
    setup();
    expect(combineSignals([undefined]).signal).toBeUndefined();
  });

  it('하나가 abort되면 결합 signal도 abort되고 reason을 전한다', () => {
    setup();
    const a = new AbortController();
    const b = new AbortController();
    const { signal, release } = combineSignals([a.signal, b.signal]);
    expect(signal?.aborted).toBe(false);
    b.abort('reason-b');
    expect(signal?.aborted).toBe(true);
    expect(signal?.reason).toBe('reason-b');
    release();
  });

  it('이미 abort된 signal이 있으면 즉시 abort', () => {
    setup();
    const a = new AbortController();
    a.abort('early');
    const { signal } = combineSignals([a.signal, new AbortController().signal]);
    expect(signal?.aborted).toBe(true);
  });

  it('release 뒤에는 원래 signal의 abort가 전파되지 않는다(수동 연결만 해당)', () => {
    setup();
    const a = new AbortController();
    const b = new AbortController();
    const { signal, release } = combineSignals([a.signal, b.signal]);
    release();
    a.abort();
    expect(signal?.aborted).toBe(!removeAny);
  });
});
