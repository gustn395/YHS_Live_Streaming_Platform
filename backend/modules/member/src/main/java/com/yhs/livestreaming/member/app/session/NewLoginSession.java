package com.yhs.livestreaming.member.app.session;

import java.time.Instant;

/**
 * 저장할 로그인 세션(MB-D014). Refresh Token 원문은 담지 않고 SHA-256 해시(hex)만 담는다.
 *
 * @param sessionId        SecureRandom 256비트 Base64url(43자)
 * @param memberId         회원 ID
 * @param refreshTokenHash Refresh Token 원문의 SHA-256 hex(64자)
 * @param expiresAt        세션·Refresh Token 공통 절대 만료 시각(최초 발급 + 7일)
 */
public record NewLoginSession(String sessionId, long memberId, String refreshTokenHash, Instant expiresAt) {

    @Override
    public String toString() {
        return "NewLoginSession[sessionId=****, memberId=" + memberId + ", refreshTokenHash=****, expiresAt="
                + expiresAt + "]";
    }
}
