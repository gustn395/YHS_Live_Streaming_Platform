package com.yhs.livestreaming.member.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.limit.LoginAttemptLimiter.FailureOutcome;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties;
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
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * MB-T020: 로그인 실패 제한(MB-D017). username 기준 연속 실패, 10번째 실패부터 15분 차단(연장 없음), 1차 인증 성공 시 초기화,
 * 동시 요청에서도 카운터 누락·차단 연장이 없다. Redis 장애는 503(MB-D019).
 */
@DataRedisTest
class RedisLoginAttemptLimiterTest extends RedisTestSupport {

    @Autowired
    private StringRedisTemplate template;

    private RedisLoginAttemptLimiter limiter() {
        return new RedisLoginAttemptLimiter(template, MemberLimitProperties.defaults());
    }

    @Test
    void tenthConsecutiveFailureBlocksForFifteenMinutes() {
        RedisLoginAttemptLimiter limiter = limiter();
        for (int i = 1; i <= 9; i++) {
            assertThat(limiter.recordFailure("tester01").blocked()).as("failure %d", i).isFalse();
        }

        FailureOutcome tenth = limiter.recordFailure("tester01");

        assertThat(tenth.blocked()).isTrue();
        assertThat(tenth.retryAfter()).isEqualTo(Duration.ofMinutes(15));
        assertThat(limiter.blockedFor("tester01")).hasValueSatisfying(
                left -> assertThat(left).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(15)));
        assertThat(template.keys("auth:login:fail:*")).isEmpty(); // 차단 키를 만들면서 카운터를 초기화
    }

    @Test
    void failuresWhileBlockedDoNotExtendTheBlock() {
        RedisLoginAttemptLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("tester01");
        }
        String blockKey = template.keys("auth:login:block:*").iterator().next();
        template.expire(blockKey, Duration.ofSeconds(5));

        FailureOutcome again = limiter.recordFailure("tester01");

        assertThat(again.blocked()).isTrue();
        assertThat(template.getExpire(blockKey)).isBetween(1L, 5L);
        assertThat(template.keys("auth:login:fail:*")).isEmpty();
    }

    @Test
    void failureCounterLivesTwentyFourHoursFromLastFailure() {
        RedisLoginAttemptLimiter limiter = limiter();
        limiter.recordFailure("tester01");
        String failKey = template.keys("auth:login:fail:*").iterator().next();
        template.expire(failKey, Duration.ofSeconds(5));

        limiter.recordFailure("tester01");

        assertThat(template.getExpire(failKey)).isGreaterThan(Duration.ofHours(23).toSeconds());
        assertThat(template.opsForValue().get(failKey)).isEqualTo("2");
    }

    @Test
    void successResetsTheCounter() {
        RedisLoginAttemptLimiter limiter = limiter();
        for (int i = 0; i < 9; i++) {
            limiter.recordFailure("tester01");
        }

        assertThat(limiter.recordSuccess("tester01")).isEmpty();

        assertThat(limiter.recordFailure("tester01").blocked()).isFalse();
        assertThat(template.opsForValue().get(template.keys("auth:login:fail:*").iterator().next())).isEqualTo("1");
    }

    @Test
    void successRaceAgainstABlockCreatedMeanwhileIsReportedAsBlocked() {
        RedisLoginAttemptLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("tester01"); // 비밀번호 검증 중 다른 요청이 차단 키를 만든 상황
        }

        assertThat(limiter.recordSuccess("tester01")).hasValueSatisfying(left -> assertThat(left).isPositive());
    }

    @Test
    void usernamesAreCountedSeparatelyAndNotStoredInPlainText() {
        RedisLoginAttemptLimiter limiter = limiter();
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("tester01");
        }

        assertThat(limiter.blockedFor("tester02")).isEmpty();
        assertThat(template.keys("*")).allSatisfy(key -> assertThat(key).doesNotContain("tester01"));
    }

    @Test
    void concurrentFailuresBlockExactlyAtTheTenth() throws Exception {
        RedisLoginAttemptLimiter limiter = limiter();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<FailureOutcome>> calls = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                calls.add(() -> limiter.recordFailure("racer01"));
            }
            long notBlocked = 0;
            for (Future<FailureOutcome> outcome : pool.invokeAll(calls)) {
                if (!outcome.get().blocked()) {
                    notBlocked++;
                }
            }
            assertThat(notBlocked).isEqualTo(9);
            assertThat(template.keys("auth:login:block:*")).hasSize(1);
            assertThat(template.getExpire(template.keys("auth:login:block:*").iterator().next()))
                    .isGreaterThan(Duration.ofMinutes(14).toSeconds());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unreachableRedisIsStoreUnavailable() {
        try (RedisLoginSessionStoreTest.UnreachableRedis unreachable = RedisLoginSessionStoreTest.unreachableRedis()) {
            RedisLoginAttemptLimiter broken = new RedisLoginAttemptLimiter(unreachable.template(),
                    MemberLimitProperties.defaults());

            assertThatThrownBy(() -> broken.blockedFor("tester01"))
                    .isInstanceOf(LoginSessionStoreUnavailableException.class);
            assertThatThrownBy(() -> broken.recordFailure("tester01"))
                    .isInstanceOf(LoginSessionStoreUnavailableException.class);
        }
    }
}
