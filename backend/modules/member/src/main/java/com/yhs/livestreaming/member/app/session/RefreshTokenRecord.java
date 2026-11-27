package com.yhs.livestreaming.member.app.session;

import java.time.Instant;

/**
 * Refresh Token 해시로 찾은 로그인 세션(MB-D021). 해시 키가 있고 연결된 세션이 같은 회원 것일 때만 만든다.
 *
 * @param sessionId 로그인 세션 ID
 * @param memberId  회원 ID
 * @param expiresAt 세션·Refresh 공통 절대 만료 시각(세션에 저장된 값)
 * @param current   세션의 현재 Refresh 해시와 같으면 true, 이미 교체된(USED) 토큰이면 false
 */
public record RefreshTokenRecord(String sessionId, long memberId, Instant expiresAt, boolean current) {

    @Override
    public String toString() {
        return "RefreshTokenRecord[sessionId=****, memberId=" + memberId + ", expiresAt=" + expiresAt + ", current="
                + current + "]";
    }
}
