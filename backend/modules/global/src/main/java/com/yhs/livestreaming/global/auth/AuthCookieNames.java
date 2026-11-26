package com.yhs.livestreaming.global.auth;

/**
 * global이 읽는 인증 쿠키 이름(MB-D013, 공개 API). HttpOnly·Secure·SameSite=Strict, Domain 미지정. {@code __Host-} 접두사는
 * Path=/·Secure·Domain 없음일 때만 브라우저가 받는다. Refresh·OTP Challenge 쿠키는 member 경로에 묶여 있어 member가 정의한다.
 */
public final class AuthCookieNames {

    /** Access Token(JWT). Path=/, Max-Age 15분. 인증 필터가 이 쿠키만 읽는다. */
    public static final String ACCESS_TOKEN = "__Host-yhs_at";

    private AuthCookieNames() {
    }
}
