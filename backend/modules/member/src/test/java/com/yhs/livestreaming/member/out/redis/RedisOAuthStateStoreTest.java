package com.yhs.livestreaming.member.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;

/**
 * MB-T049(저장소): OAuth 임시 인증 상태(MB-D024). TTL, state가 맞을 때만 조회·원자 소비, state가 다르면(늦은 콜백) 최신 상태
 * 보존, 동시 소비는 한 건만.
 */
@DataRedisTest
@Import(RedisOAuthStateStore.class)
class RedisOAuthStateStoreTest extends RedisTestSupport {

    private static final String HANDLE = "a".repeat(64);

    @Autowired
    private RedisOAuthStateStore store;

    private void saveDefault() {
        store.save(HANDLE, Map.of("state", "s-1", "at.code_verifier", "v", "ap.nonce", "n"), Duration.ofMinutes(10));
    }

    @Test
    void savesUnderAHashedKeyWithTheGivenTtl() {
        saveDefault();

        assertThat(redisTemplate.keys("auth:oauth:*")).containsExactly("auth:oauth:" + HANDLE);
        assertThat(redisTemplate.getExpire("auth:oauth:" + HANDLE)).isBetween(590L, 600L);
    }

    @Test
    void findReturnsFieldsOnlyForTheMatchingStateAndDoesNotConsume() {
        saveDefault();

        assertThat(store.find(HANDLE, "other")).isEmpty();
        assertThat(store.find(HANDLE, "s-1")).hasValueSatisfying(f -> assertThat(f).containsEntry("at.code_verifier", "v"));
        assertThat(redisTemplate.hasKey("auth:oauth:" + HANDLE)).isTrue();
    }

    @Test
    void consumeReturnsAndDeletesOnceForTheMatchingState() {
        saveDefault();

        assertThat(store.consume(HANDLE, "s-1")).hasValueSatisfying(f -> assertThat(f).containsEntry("state", "s-1")
                .containsEntry("ap.nonce", "n"));
        assertThat(redisTemplate.hasKey("auth:oauth:" + HANDLE)).isFalse();
        assertThat(store.consume(HANDLE, "s-1")).isEmpty();
    }

    @Test
    void consumeWithADifferentStateKeepsTheStoredState() { // 이전 요청의 늦은 콜백
        saveDefault();

        assertThat(store.consume(HANDLE, "older-state")).isEmpty();
        assertThat(redisTemplate.hasKey("auth:oauth:" + HANDLE)).isTrue();
    }

    @Test
    void concurrentConsumesOfOneStateLetExactlyOneSucceed() throws Exception {
        saveDefault();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Optional<Map<String, String>>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    return store.consume(HANDLE, "s-1");
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            int consumed = 0;
            for (Future<Optional<Map<String, String>>> future : futures) {
                consumed += future.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0;
            }
            assertThat(consumed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void wrongTypeKeyIsTreatedAsMissing() {
        redisTemplate.opsForValue().set("auth:oauth:" + HANDLE, "string");

        assertThat(store.consume(HANDLE, "s-1")).isEmpty();
        assertThat(store.find(HANDLE, "s-1")).isEmpty();
    }

    @Test
    void unreachableRedisIsStoreUnavailable() {
        try (RedisLoginSessionStoreTest.UnreachableRedis unreachable = RedisLoginSessionStoreTest.unreachableRedis()) {
            RedisOAuthStateStore broken = new RedisOAuthStateStore(unreachable.template());

            assertThatThrownBy(() -> broken.save(HANDLE, Map.of("state", "s"), Duration.ofMinutes(10)))
                    .isInstanceOf(LoginSessionStoreUnavailableException.class);
            assertThatThrownBy(() -> broken.consume(HANDLE, "s")).isInstanceOf(LoginSessionStoreUnavailableException.class);
        }
    }
}
