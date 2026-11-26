package com.yhs.livestreaming.member.app.session;

/**
 * 로그인 세션·Refresh Token 저장 포트(MB-D014, G-D007). 저장소에 닿지 못하면
 * {@link com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException}을 던진다(MB-D019).
 */
public interface LoginSessionStore {

    /**
     * 세션과 Refresh Token 해시를 같은 절대 만료 시각으로 한 번에 저장한다. Session ID나 Refresh 해시 키가 이미 있으면
     * 아무것도 쓰지 않고 {@link CreateResult#COLLISION}을 돌려준다.
     */
    CreateResult create(NewLoginSession session);

    enum CreateResult {
        CREATED,
        COLLISION
    }
}
