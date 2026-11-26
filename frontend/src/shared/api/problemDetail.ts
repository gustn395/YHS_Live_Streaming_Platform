import type { FieldError, ProblemInfo } from './apiError';

const PROBLEM_JSON = 'application/problem+json';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isFieldError(value: unknown): value is FieldError {
  return isRecord(value) && typeof value.field === 'string' && typeof value.message === 'string';
}

/**
 * `application/problem+json` 응답 본문에서 ProblemDetail 값을 꺼낸다(G-D003).
 * 형식이 맞지 않으면 null. 없는 필드는 채우지 않는다.
 */
export function parseProblemDetail(contentType: string | undefined, body: unknown): ProblemInfo | null {
  if (!contentType?.toLowerCase().includes(PROBLEM_JSON) || !isRecord(body)) return null;
  const info: ProblemInfo = {
    fieldErrors: Array.isArray(body.errors) ? body.errors.filter(isFieldError) : [],
  };
  if (typeof body.code === 'string') info.code = body.code;
  if (typeof body.title === 'string') info.title = body.title;
  if (typeof body.detail === 'string') info.detail = body.detail;
  return info;
}
