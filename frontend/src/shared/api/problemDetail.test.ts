import { describe, expect, it } from 'vitest';
import { parseProblemDetail } from './problemDetail';

const body = { code: 'COMMON_INVALID_INPUT', errors: [] };

// FE-T002: Content-Type의 미디어 타입이 정확히 application/problem+json일 때만 ProblemDetail로 본다(Bot 리뷰 반영)
describe('parseProblemDetail Content-Type 판별', () => {
  it.each(['application/problem+json', 'application/problem+json; charset=utf-8', ' APPLICATION/Problem+JSON ;charset=UTF-8'])(
    '허용: %s',
    (contentType) => {
      expect(parseProblemDetail(contentType, body)).toEqual({ code: 'COMMON_INVALID_INPUT', fieldErrors: [] });
    },
  );

  it.each([
    'application/json; profile="application/problem+json"',
    'application/json',
    'application/problem+jsonx',
    'text/plain; note=application/problem+json',
  ])('거부: %s', (contentType) => {
    expect(parseProblemDetail(contentType, body)).toBeNull();
  });
});
