export interface FieldError {
  field: string;
  message: string;
}

/** ProblemDetail(G-D003)에서 꺼낸 값. Backend가 보내지 않은 값은 만들지 않는다. */
export interface ProblemInfo {
  code?: string;
  title?: string;
  detail?: string;
  fieldErrors: FieldError[];
}

export type ApiFailure =
  /** 서버가 오류 status로 응답했다. ProblemDetail이 아니면 problem은 null이다. */
  | { kind: 'http'; status: number; problem: ProblemInfo | null }
  /** 2xx인데 본문 형식이 기대와 다르다. */
  | { kind: 'invalid-response'; status: number }
  | { kind: 'network' }
  | { kind: 'timeout' }
  /** 호출자·인증 전환·Deadline으로 취소했거나, 오래된 인증 세대의 응답이라 버렸다. 화면 오류로 표시하지 않는다. */
  | { kind: 'canceled' };

export class ApiError extends Error {
  override readonly name = 'ApiError';
  readonly failure: ApiFailure;

  constructor(failure: ApiFailure, options?: { cause?: unknown }) {
    super(`API 요청 실패: ${failure.kind}`, options);
    this.failure = failure;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
