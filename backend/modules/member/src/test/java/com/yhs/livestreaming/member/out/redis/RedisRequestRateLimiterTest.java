package com.yhs.livestreaming.member.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.CommonErrorCode;
import com.yhs.livestreaming.global.error.TooManyRequestsException;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties;
import com.yhs.livestreaming.member.app.limit.RateLimitScope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;

/**
 * MB-T028: IP 기준 요청 제한(MB-D018). Redis 고정 창(Lua INCR + PEXPIRE 원자 처리), 초과 시 429 + Retry-After,
 * 저장소 장애 시 503(MB-D019).
 */
@DataRedisTest
class RedisRequestRateLimiterTest extends RedisTestSupport {

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate template;

    private RedisRequestRateLimiter limiter() {
        return new RedisRequestRateLimiter(template, MemberLimitProperties.defaults());
    }

    @Test
    void signupAllowsFivePerHourPerIpThenRejectsWithRetryAfter() {
        RedisRequestRateLimiter limiter = limiter();
        for (int i = 0; i < 5; i++) {
            limiter.acquire(RateLimitScope.SIGNUP, "203.0.113.7");
        }

        assertThatThrownBy(() -> limiter.acquire(RateLimitScope.SIGNUP, "203.0.113.7"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
                    assertThat(ex.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofHours(1));
                });
    }

    @Test
    void loginAndOtpAllowThirtyPerTenMinutes() {
        RedisRequestRateLimiter limiter = limiter();
        for (int i = 0; i < 30; i++) {
            limiter.acquire(RateLimitScope.LOGIN, "203.0.113.7");
            limiter.acquire(RateLimitScope.OTP, "203.0.113.7");
        }

        assertThatThrownBy(() -> limiter.acquire(RateLimitScope.LOGIN, "203.0.113.7"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.retryAfter()).isLessThanOrEqualTo(Duration.ofMinutes(10)));
        assertThatThrownBy(() -> limiter.acquire(RateLimitScope.OTP, "203.0.113.7"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void ipsAndScopesAreCountedSeparately() {
        RedisRequestRateLimiter limiter = limiter();
        for (int i = 0; i < 5; i++) {
            limiter.acquire(RateLimitScope.SIGNUP, "203.0.113.7");
        }

        limiter.acquire(RateLimitScope.SIGNUP, "203.0.113.8");
        limiter.acquire(RateLimitScope.LOGIN, "203.0.113.7");
    }

    @Test
    void windowStartsAtFirstRequestAndIsNotExtendedByLaterOnes() {
        RedisRequestRateLimiter limiter = limiter();
        limiter.acquire(RateLimitScope.SIGNUP, "203.0.113.7");
        String key = template.keys("ratelimit:signup:*").iterator().next();
        template.expire(key, Duration.ofSeconds(5));

        limiter.acquire(RateLimitScope.SIGNUP, "203.0.113.7");

        assertThat(template.getExpire(key)).isBetween(1L, 5L);
    }

    @Test
    void rawIpIsNotUsedAsKey() {
        limiter().acquire(RateLimitScope.SIGNUP, "203.0.113.7");

        assertThat(template.keys("*")).singleElement().satisfies(key -> {
            assertThat(key).startsWith("ratelimit:signup:").doesNotContain("203.0.113.7");
            assertThat(key.substring("ratelimit:signup:".length())).matches("[0-9a-f]{64}");
        });
    }

    @Test
    void concurrentRequestsNeverExceedTheLimit() throws Exception {
        RedisRequestRateLimiter limiter = limiter();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                calls.add(() -> {
                    try {
                        limiter.acquire(RateLimitScope.SIGNUP, "203.0.113.9");
                        return true;
                    } catch (TooManyRequestsException ex) {
                        return false;
                    }
                });
            }
            long allowed = 0;
            for (Future<Boolean> result : pool.invokeAll(calls)) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unreachableRedisIsServiceUnavailable() {
        RedisRequestRateLimiter broken = new RedisRequestRateLimiter(
                RedisLoginSessionStoreTest.unreachableRedis(), MemberLimitProperties.defaults());

        assertThatThrownBy(() -> broken.acquire(RateLimitScope.SIGNUP, "203.0.113.7"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.RATE_LIMIT_UNAVAILABLE));
    }
}
