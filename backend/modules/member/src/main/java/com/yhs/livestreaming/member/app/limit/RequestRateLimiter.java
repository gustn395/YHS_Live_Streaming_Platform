package com.yhs.livestreaming.member.app.limit;

/**
 * IP 기준 요청 제한 포트(MB-D018). 초과하면 {@code TooManyRequestsException}(429 {@code COMMON_TOO_MANY_REQUESTS}),
 * 저장소를 쓸 수 없으면 503 {@code COMMON_RATE_LIMIT_UNAVAILABLE}을 던진다(MB-D019).
 */
public interface RequestRateLimiter {

    void acquire(RateLimitScope scope, String clientIp);
}
