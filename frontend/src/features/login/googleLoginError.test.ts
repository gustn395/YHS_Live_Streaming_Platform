import { describe, expect, it } from 'vitest';
import { GOOGLE_LOGIN_ERROR_TEXT, googleLoginErrorMessage } from './googleLoginError';

// FE-T038: Google 로그인 오류 안내(FE-D038, MB-D024의 6개 코드)
describe('googleLoginErrorMessage', () => {
  it.each([
    'oauth_failed',
    'oauth_email_unverified',
    'account_disabled',
    'rate_limited',
    'otp_delivery_failed',
    'temporarily_unavailable',
  ] as const)('%s는 그 코드의 고정 문구다', (code) => {
    expect(googleLoginErrorMessage(code)).toBe(GOOGLE_LOGIN_ERROR_TEXT[code]);
  });

  it('6개 코드의 문구는 서로 다르고 일반 문구와도 다르다', () => {
    const texts = Object.values(GOOGLE_LOGIN_ERROR_TEXT);
    expect(new Set(texts).size).toBe(7);
  });

  it.each(['unknown', '', 'OAUTH_FAILED', '<img src=x onerror=alert(1)>', 'oauth_failed '])(
    '알 수 없는 값 %j는 일반 문구이고 받은 값을 담지 않는다',
    (value) => {
      const message = googleLoginErrorMessage(value);
      expect(message).toBe(GOOGLE_LOGIN_ERROR_TEXT.unknown);
      if (value !== '') expect(message).not.toContain(value);
    },
  );

  it('값이 없으면(null) 안내하지 않는다', () => {
    expect(googleLoginErrorMessage(null)).toBeNull();
  });

  it('rate_limited는 대기 시간을 말하지 않는다(302에는 Retry-After가 없다)', () => {
    expect(GOOGLE_LOGIN_ERROR_TEXT.rate_limited).not.toMatch(/\d/);
  });
});
