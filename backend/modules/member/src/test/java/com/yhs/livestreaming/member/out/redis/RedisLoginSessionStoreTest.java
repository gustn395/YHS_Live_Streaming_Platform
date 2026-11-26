package com.yhs.livestreaming.member.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.LoginSessionStatus;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.session.LoginSessionStore.CreateResult;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * MB-T027: 로그인 세션·Refresh Token 저장(MB-D014, G-D007). 실제 Redis에서 Lua 원자 저장·키 충돌·TTL·저장 실패를 확인한다.
 */
@DataRedisTest
@Import({RedisLoginSessionStore.class, RedisLoginSessionStoreTest.ClockConfig.class})
class RedisLoginSessionStoreTest extends RedisTestSupport {

    private static final String SID = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";
    private static final String REFRESH_HASH = "a".repeat(64);
    private static final String SESSION_KEY = "auth:session:" + SID;
    private static final String REFRESH_KEY = "auth:refresh:" + REFRESH_HASH;

    @Autowired
    private RedisLoginSessionStore store;

    @Test
    void createStoresSessionAndRefreshHashWithSameAbsoluteExpiry() {
        Instant expiresAt = Instant.now().plus(Duration.ofDays(7));

        CreateResult result = store.create(new NewLoginSession(SID, 42L, REFRESH_HASH, expiresAt));

        assertThat(result).isEqualTo(CreateResult.CREATED);
        Map<Object, Object> session = redisTemplate.opsForHash().entries(SESSION_KEY);
        assertThat(session).containsEntry("memberId", "42").containsEntry("refreshHash", REFRESH_HASH)
                .containsEntry("expiresAt", Long.toString(expiresAt.toEpochMilli()));
        Map<Object, Object> refresh = redisTemplate.opsForHash().entries(REFRESH_KEY);
        assertThat(refresh).containsEntry("sid", SID).containsEntry("memberId", "42")
                .containsEntry("expiresAt", Long.toString(expiresAt.toEpochMilli()));
        // PEXPIRETIME(Redis 7.0+): 저장한 절대 만료 시각(ms)을 그대로 돌려준다
        assertThat(absoluteExpiry(SESSION_KEY)).isEqualTo(expiresAt.toEpochMilli());
        assertThat(absoluteExpiry(REFRESH_KEY)).isEqualTo(expiresAt.toEpochMilli());
    }

    @Test
    void existingSessionIdIsACollisionAndNothingIsOverwrittenOrCreated() {
        redisTemplate.opsForHash().put(SESSION_KEY, "memberId", "7");

        CreateResult result = store.create(new NewLoginSession(SID, 42L, REFRESH_HASH, future()));

        assertThat(result).isEqualTo(CreateResult.COLLISION);
        assertThat(redisTemplate.opsForHash().get(SESSION_KEY, "memberId")).isEqualTo("7");
        assertThat(redisTemplate.hasKey(REFRESH_KEY)).isFalse();
    }

    @Test
    void existingRefreshHashIsACollisionAndNoSessionIsCreated() {
        redisTemplate.opsForValue().set(REFRESH_KEY, "other-type"); // 다른 자료형이어도 존재 확인에서 걸러진다

        CreateResult result = store.create(new NewLoginSession(SID, 42L, REFRESH_HASH, future()));

        assertThat(result).isEqualTo(CreateResult.COLLISION);
        assertThat(redisTemplate.hasKey(SESSION_KEY)).isFalse();
        assertThat(redisTemplate.opsForValue().get(REFRESH_KEY)).isEqualTo("other-type");
    }

    @Test
    void sessionAlreadyExpiredIsRejectedBeforeWriting() {
        assertThatThrownBy(() -> store.create(new NewLoginSession(SID, 42L, REFRESH_HASH, Instant.now())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(redisTemplate.keys("auth:*")).isEmpty();
    }

    @Test
    void verifyReportsActiveNotFoundAndMismatch() {
        store.create(new NewLoginSession(SID, 42L, REFRESH_HASH, future()));

        assertThat(store.verify(SID, 42L)).isEqualTo(LoginSessionStatus.ACTIVE);
        assertThat(store.verify(SID, 43L)).isEqualTo(LoginSessionStatus.MEMBER_MISMATCH);
        assertThat(store.verify("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", 42L))
                .isEqualTo(LoginSessionStatus.NOT_FOUND);
    }

    @Test
    void rawRefreshTokenOrPlainValuesAreNotStoredAsKeys() {
        store.create(new NewLoginSession(SID, 42L, REFRESH_HASH, future()));

        Set<String> keys = redisTemplate.keys("*");
        assertThat(keys).containsExactlyInAnyOrder(SESSION_KEY, REFRESH_KEY);
    }

    @Test
    void unreachableRedisIsReportedAsStoreUnavailable() {
        try (UnreachableRedis unreachable = unreachableRedis()) {
            RedisLoginSessionStore broken = new RedisLoginSessionStore(unreachable.template(),
                    java.time.Clock.systemUTC());

            assertThatThrownBy(() -> broken.verify(SID, 42L)).isInstanceOf(LoginSessionStoreUnavailableException.class);
            assertThatThrownBy(() -> broken.create(new NewLoginSession(SID, 42L, REFRESH_HASH, future())))
                    .isInstanceOf(LoginSessionStoreUnavailableException.class);
        }
    }

    /** 닿지 않는 Redis(127.0.0.1:1). 테스트가 끝나면 close로 연결 팩터리를 정리한다. */
    record UnreachableRedis(StringRedisTemplate template, LettuceConnectionFactory factory) implements AutoCloseable {
        @Override
        public void close() {
            factory.destroy();
        }
    }

    static UnreachableRedis unreachableRedis() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 1),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build());
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return new UnreachableRedis(template, factory);
    }

    private long absoluteExpiry(String key) {
        return redisTemplate.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "return redis.call('PEXPIRETIME', KEYS[1])", Long.class), java.util.List.of(key));
    }

    private static Instant future() {
        return Instant.now().plus(Duration.ofDays(7));
    }

    @Test
    void expiryCheckUsesTheInjectedClock() {
        Instant now = Instant.now();
        RedisLoginSessionStore aheadOneDay = new RedisLoginSessionStore(redisTemplate,
                java.time.Clock.fixed(now.plus(Duration.ofDays(1)), java.time.ZoneOffset.UTC));

        assertThatThrownBy(() -> aheadOneDay.create(
                new NewLoginSession(SID, 42L, REFRESH_HASH, now.plus(Duration.ofHours(12)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(redisTemplate.keys("auth:*")).isEmpty();
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @org.springframework.context.annotation.Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }
    }
}
