import { describe, expect, it } from 'vitest';
import { ApiError } from './apiError';
import { createCsrfTokenSource } from './csrfToken';

function setCookie(value: string | null) {
  document.cookie =
    value === null ? 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/' : `XSRF-TOKEN=${value}; path=/`;
}

/** 발급 API 대역: 호출마다 끝낼 시점을 테스트가 정한다. 성공하면 지정한 쿠키를 둔다. */
function controlledFetch() {
  const pending: { resolve: (cookie: string | null) => void; reject: (error: unknown) => void }[] = [];
  const fetchToken = () =>
    new Promise<void>((resolve, reject) => {
      pending.push({
        resolve: (cookie) => {
          if (cookie !== null) setCookie(cookie);
          resolve();
        },
        reject,
      });
    });
  return { fetchToken, calls: () => pending.length, call: (i: number) => pending[i]! };
}

const flush = () => new Promise((r) => setTimeout(r, 0));

// FE-T022(보완): 403 뒤 강제 재확보와 진행 중 발급의 경쟁(FE-D031 (4), S6 리뷰 지적 5)
describe('강제 재확보와 진행 중 발급', () => {
  it('일반 발급 중에 403을 받으면, 그 뒤의 요청은 진행 중 발급이 끝난 뒤 새 발급을 한 번 더 거친다', async () => {
    const f = controlledFetch();
    const source = createCsrfTokenSource(f.fetchToken);
    const before = source.obtain(); // 쿠키 없음 → 일반 발급 1
    await flush();
    source.markInvalid(null);
    const after = source.obtain();
    f.call(0).resolve('old');
    await expect(before).resolves.toBe('old');
    await flush();
    expect(f.calls()).toBe(2); // 403 이후 요청은 이전 발급에 합류하지 않는다
    f.call(1).resolve('new');
    await expect(after).resolves.toBe('new');
  });

  it('강제 발급 중 동시 요청은 같은 발급에 합류한다', async () => {
    setCookie('stale');
    const f = controlledFetch();
    const source = createCsrfTokenSource(f.fetchToken);
    source.markInvalid('stale');
    const a = source.obtain();
    const b = source.obtain();
    await flush();
    expect(f.calls()).toBe(1);
    f.call(0).resolve('fresh');
    await expect(Promise.all([a, b])).resolves.toEqual(['fresh', 'fresh']);
  });

  it('강제 발급 중 새 403이 오면 그 뒤의 요청은 발급을 한 번 더 거친다', async () => {
    setCookie('t0');
    const f = controlledFetch();
    const source = createCsrfTokenSource(f.fetchToken);
    source.markInvalid('t0');
    const first = source.obtain();
    await flush();
    source.markInvalid('t0'); // 발급이 끝나기 전 같은 쿠키로 보낸 다른 요청이 또 403
    const second = source.obtain();
    f.call(0).resolve('t1');
    await expect(first).resolves.toBe('t1');
    await flush();
    expect(f.calls()).toBe(2);
    f.call(1).resolve('t2');
    await expect(second).resolves.toBe('t2');
  });

  it('성공한 강제 발급 뒤에는 다시 강제하지 않는다', async () => {
    setCookie('t0');
    const f = controlledFetch();
    const source = createCsrfTokenSource(f.fetchToken);
    source.markInvalid('t0');
    const first = source.obtain();
    await flush();
    f.call(0).resolve('t1');
    await first;
    await expect(source.obtain()).resolves.toBe('t1');
    expect(f.calls()).toBe(1);
  });

  it('강제 발급이 실패하면 복구 필요 상태가 남아 다음 요청이 다시 발급한다', async () => {
    setCookie('t0');
    const f = controlledFetch();
    const source = createCsrfTokenSource(f.fetchToken);
    source.markInvalid('t0');
    const first = source.obtain();
    await flush();
    f.call(0).reject(new ApiError({ kind: 'network' }));
    await expect(first).rejects.toBeInstanceOf(ApiError);
    const second = source.obtain();
    await flush();
    expect(f.calls()).toBe(2);
    f.call(1).resolve('t1');
    await expect(second).resolves.toBe('t1');
  });

  it('403을 받은 토큰이 이미 다른 값으로 바뀌었으면 강제 재확보를 예약하지 않는다', async () => {
    setCookie('renewed');
    const f = controlledFetch();
    const source = createCsrfTokenSource(f.fetchToken);
    source.markInvalid('old-sent-token'); // 예: 이전 인증 세대 요청의 늦은 403
    await expect(source.obtain()).resolves.toBe('renewed');
    expect(f.calls()).toBe(0);
  });

  it('대기 중 취소된 요청만 빠지고 강제 발급은 계속된다', async () => {
    setCookie('t0');
    const f = controlledFetch();
    const source = createCsrfTokenSource(f.fetchToken);
    source.markInvalid('t0');
    const controller = new AbortController();
    const canceled = source.obtain(controller.signal);
    const kept = source.obtain();
    await flush();
    controller.abort();
    await expect(canceled).rejects.toMatchObject({ failure: { kind: 'canceled' } });
    f.call(0).resolve('t1');
    await expect(kept).resolves.toBe('t1');
    expect(f.calls()).toBe(1);
  });
});
