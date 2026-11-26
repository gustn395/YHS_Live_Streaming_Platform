package com.yhs.livestreaming.member.out.redis;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.limit.LoginAttemptLimiter;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties.LoginFailure;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 로그인 실패 제한(MB-D017). 실패 기록·차단 판정·차단 키 생성·초기화는 Lua로 원자 처리한다.
 * 키에는 username 원문 대신 SHA-256을 쓴다.
 */
@Component
public class RedisLoginAttemptLimiter implements LoginAttemptLimiter {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> FAIL = RedisLuaScripts.load("login_fail", List.class);
    private static final DefaultRedisScript<Long> SUCCESS = RedisLuaScripts.load("login_success", Long.class);

    private final StringRedisTemplate redis;
    private final LoginFailure policy;

    public RedisLoginAttemptLimiter(StringRedisTemplate redis, MemberLimitProperties limits) {
        this.redis = redis;
        this.policy = limits.loginFailure();
    }

    @Override
    public Optional<Duration> blockedFor(String username) {
        Long ttl;
        try {
            ttl = redis.getExpire(RedisKeys.loginBlock(username), TimeUnit.MILLISECONDS);
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        return ttl != null && ttl > 0 ? Optional.of(Duration.ofMillis(ttl)) : Optional.empty();
    }

    @Override
    public FailureOutcome recordFailure(String username) {
        List<?> result;
        try {
            result = redis.execute(FAIL, List.of(RedisKeys.loginFailure(username), RedisKeys.loginBlock(username)),
                    Integer.toString(policy.maxFailures()), Long.toString(policy.counterTtl().toMillis()),
                    Long.toString(policy.block().toMillis()));
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        if (result == null || result.size() != 2) {
            throw new LoginSessionStoreUnavailableException("로그인 실패 기록 결과를 확인할 수 없다", null);
        }
        boolean blocked = ((Number) result.get(0)).longValue() == 1;
        Duration retryAfter = blocked ? Duration.ofMillis(((Number) result.get(1)).longValue()) : Duration.ZERO;
        return new FailureOutcome(blocked, retryAfter);
    }

    @Override
    public Optional<Duration> recordSuccess(String username) {
        Long blocked;
        try {
            blocked = redis.execute(SUCCESS,
                    List.of(RedisKeys.loginFailure(username), RedisKeys.loginBlock(username)));
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        if (blocked == null) {
            throw new LoginSessionStoreUnavailableException("로그인 성공 기록 결과를 확인할 수 없다", null);
        }
        return blocked > 0 ? Optional.of(Duration.ofMillis(blocked)) : Optional.empty();
    }

    private static LoginSessionStoreUnavailableException unavailable(DataAccessException ex) {
        return new LoginSessionStoreUnavailableException("로그인 제한 저장소를 사용할 수 없다", ex);
    }
}
