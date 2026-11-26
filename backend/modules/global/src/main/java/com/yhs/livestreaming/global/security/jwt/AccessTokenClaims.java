package com.yhs.livestreaming.global.security.jwt;

import java.time.Instant;

/**
 * 검증을 통과한 Access Token의 Claim(MB-D012).
 *
 * @param memberId  {@code sub}(양수)
 * @param role      {@code role}(허용된 회원 역할)
 * @param sessionId {@code sid}(로그인 세션 ID)
 * @param expiresAt {@code exp}
 */
public record AccessTokenClaims(long memberId, String role, String sessionId, Instant expiresAt) {
}
