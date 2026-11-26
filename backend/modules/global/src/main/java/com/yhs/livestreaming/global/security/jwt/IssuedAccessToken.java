package com.yhs.livestreaming.global.security.jwt;

import java.time.Instant;

/**
 * 발급한 Access Token. {@link #toString()}은 토큰 값을 가린다(로그 노출 방지).
 *
 * @param value     JWT 문자열(쿠키 값)
 * @param expiresAt 만료 시각({@code exp})
 */
public record IssuedAccessToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedAccessToken[value=****, expiresAt=" + expiresAt + "]";
    }
}
