package com.yhs.livestreaming.member.app.session;

import java.util.Optional;

/**
 * 로그인 세션·Refresh Token 저장 포트(MB-D014, MB-D021, MB-D022, G-D007). 저장소에 닿지 못하면
 * {@link com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException}을 던진다(MB-D019).
 */
public interface LoginSessionStore {

    /**
     * 세션과 Refresh Token 해시를 같은 절대 만료 시각으로 한 번에 저장한다. Session ID나 Refresh 해시 키가 이미 있으면
     * 아무것도 쓰지 않고 {@link CreateResult#COLLISION}을 돌려준다.
     */
    CreateResult create(NewLoginSession session);

    /**
     * Refresh Token 해시로 세션을 찾는다. 해시 키가 없거나, 연결된 세션이 없거나, 세션 소유 회원이 다르면 비어 있다.
     * 결과는 판단 참고용이며 최종 판단은 {@link #rotate}가 다시 한다.
     */
    Optional<RefreshTokenRecord> findRefresh(String refreshTokenHash);

    /**
     * Refresh Token을 원자적으로 교체한다. 제출된 해시가 이미 교체된 것이면 세션을 폐기하고 {@link RotateResult#REUSED},
     * 세션이 없거나 관계·만료가 맞지 않으면 아무것도 쓰지 않고 {@link RotateResult#INVALID}, 새 해시 키가 이미 있으면
     * 아무것도 쓰지 않고 {@link RotateResult#COLLISION}이다. 세션을 새로 만들지 않는다.
     */
    RotateResult rotate(RefreshRotation rotation);

    /** 세션 소유 회원이 같을 때만 세션과 그 세션의 현재 Refresh 해시 키를 지운다. 없으면 아무것도 하지 않는다. */
    RevokeResult revoke(String sessionId, long memberId);

    enum CreateResult {
        CREATED,
        COLLISION
    }

    enum RotateResult {
        ROTATED,
        REUSED,
        INVALID,
        COLLISION
    }

    enum RevokeResult {
        REVOKED,
        NOT_FOUND,
        MEMBER_MISMATCH
    }
}
