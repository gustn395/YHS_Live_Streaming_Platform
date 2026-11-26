package com.yhs.livestreaming.global.auth;

/**
 * 로그인 세션 저장소(Redis 등)에 접근할 수 없다. 인증을 통과시키지 않고 503으로 응답한다(G-D007).
 * 메시지에는 접속 정보가 들어갈 수 있으므로 응답 본문에 쓰지 않는다.
 */
public class LoginSessionStoreUnavailableException extends RuntimeException {

    public LoginSessionStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
