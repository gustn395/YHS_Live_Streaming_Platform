import { describe, expect, it } from 'vitest';
import { ApiRequestError } from './requestOptions';
import { toApiPath } from './urlGuard';

// FE-T005: /api/ 밖을 가리키는 경로는 요청 전에 거부한다(FE-D013).
describe('toApiPath', () => {
  it.each([
    ['/v1/members', '/v1/members'],
    ['/v1/members/42/profile', '/v1/members/42/profile'],
    ['/v1/search/%ED%95%9C%EA%B8%80', '/v1/search/%ED%95%9C%EA%B8%80'],
  ])('허용: %s', (input, expected) => {
    expect(toApiPath(input)).toBe(expected);
  });

  it.each([
    ['절대 URL', 'https://evil.example/v1'],
    ['스킴 상대 URL', '//evil.example/v1'],
    ['슬래시로 시작하지 않음', 'v1/members'],
    ['빈 문자열', ''],
    ['상위 경로 세그먼트', '/v1/../../admin'],
    ['현재 경로 세그먼트', '/v1/./members'],
    ['인코딩된 상위 경로', '/v1/%2e%2e/admin'],
    ['인코딩된 슬래시', '/v1%2f..%2fadmin'],
    ['인코딩된 역슬래시', '/v1%5cadmin'],
    ['역슬래시', '/v1\\..\\admin'],
    ['쿼리 문자열', '/v1/members?x=1'],
    ['프래그먼트', '/v1/members#x'],
    ['제어 문자', '/v1/mem\nbers'],
    ['이중 인코딩된 상위 경로', '/v1/%252e%252e/admin'],
    ['잘못된 퍼센트 인코딩', '/v1/%zz'],
    ['빈 세그먼트', '/v1//members'],
  ])('거부: %s', (_name, input) => {
    expect(() => toApiPath(input)).toThrow(ApiRequestError);
  });
});
