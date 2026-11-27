package com.yhs.livestreaming.member.app.session;

import java.time.Instant;

/**
 * Refresh Token 교체 요청(MB-D021). 저장소는 이 값들을 쓰기 전에 다시 확인한다.
 *
 * @param sessionId   교체할 로그인 세션 ID
 * @param memberId    세션 소유 회원 ID
 * @param currentHash 제출된 Refresh Token의 SHA-256 hex
 * @param newHash     새 Refresh Token의 SHA-256 hex
 * @param expiresAt   조회 때 확인한 세션 절대 만료 시각(바뀌었으면 교체하지 않는다)
 */
public record RefreshRotation(String sessionId, long memberId, String currentHash, String newHash, Instant expiresAt) {

    @Override
    public String toString() {
        return "RefreshRotation[sessionId=****, memberId=" + memberId + ", currentHash=****, newHash=****, expiresAt="
                + expiresAt + "]";
    }
}
