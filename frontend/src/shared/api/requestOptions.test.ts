import { describe, expect, it } from 'vitest';
import { ApiRequestError, DEFAULT_TIMEOUT_MS, resolveTimeoutMs, type ApiRequest } from './requestOptions';

// FE-T003(일부): 기본 Timeout 10초, 요청별 재정의는 1~60000ms 정수만 허용(FE-D007).
describe('resolveTimeoutMs', () => {
  it('지정하지 않으면 기본 10초', () => {
    expect(DEFAULT_TIMEOUT_MS).toBe(10_000);
    expect(resolveTimeoutMs(undefined)).toBe(10_000);
  });

  it.each([1, 2_500, 60_000])('허용 범위 정수 %d는 그대로 쓴다', (value) => {
    expect(resolveTimeoutMs(value)).toBe(value);
  });

  it.each([0, -1, 60_001, 1.5, Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY])(
    '%d는 Timeout을 끄거나 바꿀 수 없으므로 거부한다',
    (value) => {
      expect(() => resolveTimeoutMs(value)).toThrow(ApiRequestError);
    },
  );
});

// FE-T006: 인증 영향 범위(authScope)는 기본값 없이 반드시 지정해야 한다(FE-D010). tsc가 이 파일을 검사한다.
describe('ApiRequest 타입', () => {
  it('authScope를 빠뜨리면 타입 오류다', () => {
    // @ts-expect-error authScope 누락
    const missing: ApiRequest = { method: 'get', path: '/v1/members' };
    const ok: ApiRequest = { method: 'get', path: '/v1/members', authScope: 'none' };
    // @ts-expect-error 정의되지 않은 범위
    const unknownScope: ApiRequest = { method: 'get', path: '/v1/members', authScope: 'admin' };
    // @ts-expect-error baseURL 같은 Axios 옵션은 받지 않는다(FE-D013)
    const withBaseUrl: ApiRequest = { method: 'get', path: '/v1', authScope: 'none', baseURL: 'https://evil.example' };
    expect([missing, ok, unknownScope, withBaseUrl]).toHaveLength(4);
  });
});
