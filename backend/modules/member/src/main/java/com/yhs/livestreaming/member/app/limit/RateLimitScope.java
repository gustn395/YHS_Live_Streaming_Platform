package com.yhs.livestreaming.member.app.limit;

/** IP 기준 요청 제한 대상(MB-D018, Refresh는 MB-D020). */
public enum RateLimitScope {
    SIGNUP("signup"),
    LOGIN("login"),
    OTP("otp"),
    REFRESH("refresh");

    private final String key;

    RateLimitScope(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
