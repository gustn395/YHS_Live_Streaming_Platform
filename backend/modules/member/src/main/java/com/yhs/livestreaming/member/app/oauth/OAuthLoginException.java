package com.yhs.livestreaming.member.app.oauth;

/** Google 로그인 업무 거부(MB-D024): 실패 화면으로 이동할 코드만 담는다. */
public class OAuthLoginException extends RuntimeException {

    private final OAuthFailure failure;

    public OAuthLoginException(OAuthFailure failure) {
        super("Google 로그인 거부: " + failure.code());
        this.failure = failure;
    }

    public OAuthFailure failure() {
        return failure;
    }
}
