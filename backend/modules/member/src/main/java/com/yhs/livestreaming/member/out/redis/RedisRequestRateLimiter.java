package com.yhs.livestreaming.member.out.redis;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.CommonErrorCode;
import com.yhs.livestreaming.global.error.TooManyRequestsException;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties.Window;
import com.yhs.livestreaming.member.app.limit.RateLimitScope;
import com.yhs.livestreaming.member.app.limit.RequestRateLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis 고정 창 IP 요청 제한(MB-D018). 저장소 장애는 503으로 거부한다(MB-D019, fail-closed). */
@Component
public class RedisRequestRateLimiter implements RequestRateLimiter {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SCRIPT = RedisLuaScripts.load("rate_limit", List.class);

    private final StringRedisTemplate redis;
    private final MemberLimitProperties limits;

    public RedisRequestRateLimiter(StringRedisTemplate redis, MemberLimitProperties limits) {
        this.redis = redis;
        this.limits = limits;
    }

    @Override
    public void acquire(RateLimitScope scope, String clientIp) {
        Window window = limits.window(scope);
        List<?> result;
        try {
            result = redis.execute(SCRIPT, List.of(RedisKeys.rateLimit(scope.key(), clientIp)),
                    Integer.toString(window.max()), Long.toString(window.window().toMillis()));
        } catch (DataAccessException ex) {
            throw unavailable();
        }
        if (result == null || result.size() != 2) {
            throw unavailable();
        }
        if (((Number) result.get(0)).longValue() == 0) {
            throw new TooManyRequestsException(CommonErrorCode.TOO_MANY_REQUESTS,
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                    Duration.ofMillis(((Number) result.get(1)).longValue()));
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(CommonErrorCode.RATE_LIMIT_UNAVAILABLE,
                "지금은 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }
}
