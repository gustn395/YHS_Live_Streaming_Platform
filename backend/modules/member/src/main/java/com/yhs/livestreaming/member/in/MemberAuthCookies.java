package com.yhs.livestreaming.member.in;

/**
 * member가 발급하는 쿠키 이름(MB-D013). Access Token 쿠키 이름은 global이 읽으므로 global {@code AuthCookieNames}에 있고,
 * member 경로에 묶인 Refresh·OTP Challenge 쿠키는 여기 둔다(global은 도메인 모듈 경로를 참조하지 않는다).
 */
public final class MemberAuthCookies {

    /** Refresh Token(불투명 난수). Path=/api/v1/auth, Max-Age = 남은 절대 만료. */
    public static final String REFRESH_TOKEN = "__Secure-yhs_rt";
    /** Refresh Token 쿠키 Path(인증 API에만 실린다). */
    public static final String REFRESH_TOKEN_PATH = "/api/v1/auth";
    /** OTP Challenge(불투명 난수). Path=/, 보호 API 인증에는 쓰지 않는다. */
    public static final String OTP_CHALLENGE = "__Host-yhs_otp";

    private MemberAuthCookies() {
    }
}
