package com.yhs.livestreaming.member.app.oauth;

/**
 * Google 로그인 실패를 실패 화면 이동의 {@code ?error=} 값으로 나눈 것(MB-D024). 상세 원인은 응답에 싣지 않는다.
 */
public enum OAuthFailure {
    /** state·쿠키 불일치, 사용자 취소, 토큰·ID Token 검증 실패. */
    OAUTH_FAILED("oauth_failed"),
    EMAIL_UNVERIFIED("oauth_email_unverified"),
    ACCOUNT_DISABLED("account_disabled"),
    RATE_LIMITED("rate_limited"),
    OTP_DELIVERY_FAILED("otp_delivery_failed"),
    /** Redis·DB·Google 통신 장애(fail-closed). */
    TEMPORARILY_UNAVAILABLE("temporarily_unavailable");

    private final String code;

    OAuthFailure(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
