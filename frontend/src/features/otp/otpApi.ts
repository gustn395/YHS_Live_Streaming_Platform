import type { ApiClient } from '../../shared/api/apiClient';

/** 재발송 최소 간격(MB-D015). 안내용이고 최종 판단은 서버 429다. */
export const RESEND_INTERVAL_SECONDS = 60;

/** Backend OtpRequiredResponse(MB-D015). 만료 시각은 epoch ms로 바꿔 둔다(타이머용). */
export interface OtpRequired {
  otpExpiresAt: number;
}

/** Backend LoginSuccessResponse(MB-D012~D014). 로그인 성공 전환의 주체다(FE-D034). 토큰은 HttpOnly 쿠키에만 있다. */
export interface LoginPrincipal {
  memberId: number;
  role: string;
  accessTokenExpiresAt: number;
}

/**
 * Backend Instant의 JSON 형식(ISO-8601 UTC, `Z`, 소수 초 0~9자리, 규칙 원본은 test-spec FE-T031).
 * 연·월·일·시·분·초·소수를 그룹으로 꺼낸다.
 */
const ISO_INSTANT = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?Z$/;

/**
 * Backend Instant 형식의 시각 문자열을 epoch ms로 바꾼다. 형식이 다르거나 없는 날짜·시각이면 던진다(FE-D043 (1)).
 *
 * Date.parse를 쓰지 않는다: 밀리초를 넘는 소수 초 처리는 엔진마다 달라(잘라냄·반올림·해석 불가) 브라우저에 따라
 * 결과가 바뀔 수 있다. Date.UTC도 쓰지 않는다: 0~99년을 1900년대로 바꾼다. 대신 setUTCFullYear·setUTCHours로
 * 값을 그대로 넣고(소수 초는 밀리초로 잘라냄), 2월 30일·24시처럼 자동 보정된 값은 넣은 값과 다시 비교해 거부한다.
 */
function parseInstant(value: unknown, name: string): number {
  const match = typeof value === 'string' ? ISO_INSTANT.exec(value) : null;
  const fields = match?.slice(1, 7).map(Number);
  if (match === null || fields === undefined) throw new Error(`${name} 형식 오류`);
  const [year = Number.NaN, month = Number.NaN, day = Number.NaN, hour = Number.NaN, minute = Number.NaN, second = Number.NaN] = fields;
  const millis = Number((match[7] ?? '').padEnd(3, '0').slice(0, 3));

  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  date.setUTCHours(hour, minute, second, millis);
  const exact =
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day &&
    date.getUTCHours() === hour &&
    date.getUTCMinutes() === minute &&
    date.getUTCSeconds() === second;
  if (!exact) throw new Error(`${name} 형식 오류`);
  return date.getTime();
}

/**
 * 200 `OtpRequiredResponse` 계약 검사(로그인·재발송 공통). 200이 아니거나 본문이 어긋나면 던져서 공통 Client가
 * invalid-response로 바꾼다(FE-D032 (3)와 같은 원칙).
 */
export function parseOtpRequired(data: unknown, status: number): OtpRequired {
  if (status !== 200) throw new Error('OTP 대기 응답 상태 오류');
  if (typeof data !== 'object' || data === null) throw new Error('OTP 대기 응답 형식 오류');
  const body = data as Record<string, unknown>;
  if (body.status !== 'OTP_REQUIRED') throw new Error('status 형식 오류');
  return { otpExpiresAt: parseInstant(body.otpExpiresAt, 'otpExpiresAt') };
}

function parseLoginPrincipal(data: unknown, status: number): LoginPrincipal {
  if (status !== 200) throw new Error('로그인 완료 응답 상태 오류');
  if (typeof data !== 'object' || data === null) throw new Error('로그인 완료 응답 형식 오류');
  const { memberId, role, accessTokenExpiresAt } = data as Record<string, unknown>;
  if (typeof memberId !== 'number' || !Number.isSafeInteger(memberId) || memberId < 1) throw new Error('memberId 형식 오류');
  if (typeof role !== 'string' || role === '') throw new Error('role 형식 오류');
  return { memberId, role, accessTokenExpiresAt: parseInstant(accessTokenExpiresAt, 'accessTokenExpiresAt') };
}

/**
 * OTP 확인(`POST /api/v1/auth/otp/verify`). Challenge는 HttpOnly 쿠키로 간다. code는 문자열 그대로 보내 앞자리 0을
 * 지킨다(FE-D035). 실패해도 다시 보내지 않는다(시도 횟수를 쓴다).
 */
export function verifyOtp(client: ApiClient, code: string, signal?: AbortSignal): Promise<LoginPrincipal> {
  return client.request(
    {
      method: 'post',
      path: '/v1/auth/otp/verify',
      authScope: 'none',
      data: { code },
      ...(signal === undefined ? {} : { signal }),
    },
    parseLoginPrincipal,
  );
}

/** OTP 재발송(`POST /api/v1/auth/otp/resend`). 본문 없음. 실패해도 다시 보내지 않는다. */
export function resendOtp(client: ApiClient, signal?: AbortSignal): Promise<OtpRequired> {
  return client.request(
    {
      method: 'post',
      path: '/v1/auth/otp/resend',
      authScope: 'none',
      ...(signal === undefined ? {} : { signal }),
    },
    parseOtpRequired,
  );
}
