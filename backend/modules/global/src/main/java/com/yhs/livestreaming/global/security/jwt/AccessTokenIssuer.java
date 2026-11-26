package com.yhs.livestreaming.global.security.jwt;

import java.time.Instant;

/**
 * Access Token 발급(공개 API, MB-D012). 서명 키는 global만 안다. member는 OTP 최종 인증 성공 뒤 이 인터페이스로 발급한다.
 */
public interface AccessTokenIssuer {

    /**
     * HS256 JWT를 발급한다. 만료는 발급 시각 + 15분과 로그인 세션 절대 만료 시각 중 이른 쪽이다.
     *
     * @throws IllegalArgumentException 세션이 이미 끝났거나 값이 잘못된 경우
     */
    IssuedAccessToken issue(long memberId, String role, String sessionId, Instant sessionExpiresAt);
}
