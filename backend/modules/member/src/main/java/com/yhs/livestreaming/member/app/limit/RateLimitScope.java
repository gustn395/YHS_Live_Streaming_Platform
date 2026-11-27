package com.yhs.livestreaming.member.app.limit;

/** IP 기준 요청 제한 대상(MB-D018, Refresh는 MB-D020). */
public enum RateLimitScope {
    SIGNUP("signup"),
    LOGIN("login"),
    OTP("otp"),
    REFRESH("refresh"),
    /** Google 로그인 시작(MB-D024). 콜백은 LOGIN 범위를 함께 쓴다. */
    OAUTH_START("oauth-start");

    private final String key;

    RateLimitScope(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
