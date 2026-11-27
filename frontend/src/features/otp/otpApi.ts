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
 * Backend Instant의 JSON 형식(ISO-8601 UTC, `Z`, 소수 초 0~9자리). Date.parse는 RFC 1123·날짜만·시간대 없는 지역 시각도
 * 받아들이므로 먼저 이 형식인지 확인한다.
 */
const ISO_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;

/** Backend Instant 형식의 시각 문자열만 받는다. 숫자·빈 값·다른 형식·해석 불가는 거부한다. */
function parseInstant(value: unknown, name: string): number {
  if (typeof value !== 'string' || !ISO_INSTANT.test(value)) throw new Error(`${name} 형식 오류`);
  const ms = Date.parse(value);
  if (!Number.isFinite(ms)) throw new Error(`${name} 형식 오류`);
  return ms;
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
