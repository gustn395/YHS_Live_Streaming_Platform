package com.yhs.livestreaming.member.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.LoginSessionStatus;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.session.LoginSessionStore.RevokeResult;
import com.yhs.livestreaming.member.app.session.LoginSessionStore.RotateResult;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import com.yhs.livestreaming.member.app.session.RefreshRotation;
import com.yhs.livestreaming.member.app.session.RefreshTokenRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * MB-T035~MB-T037(저장소): Refresh Rotation·재사용 탐지·세션 폐기·동시성(MB-D021, MB-D022). 실제 Redis에서 Lua 원자 처리,
 * 절대 만료 시각(PEXPIRETIME 정확값), 부분 저장 없음, 다른 세션에 영향 없음을 확인한다.
 */
@DataRedisTest
@Import({RedisLoginSessionStore.class, RedisLoginSessionRotationTest.ClockConfig.class})
class RedisLoginSessionRotationTest extends RedisTestSupport {

    private static final String SID = "S".repeat(43);
    private static final String OTHER_SID = "T".repeat(43);
    private static final String HASH_1 = "1".repeat(64);
    private static final String HASH_2 = "2".repeat(64);
    private static final String HASH_3 = "3".repeat(64);
    private static final String OTHER_HASH = "9".repeat(64);
    private static final long MEMBER = 42L;

    @Autowired
    private RedisLoginSessionStore store;

    private Instant expiresAt;

    private Instant createSession(String sid, long memberId, String hash) {
        expiresAt = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MILLIS);
        store.create(new NewLoginSession(sid, memberId, hash, expiresAt));
        return expiresAt;
    }

    // ----- findRefresh -----

    @Test
    void findRefreshReportsCurrentTokenWithSessionAndExpiry() { // MB-T035
        Instant exp = createSession(SID, MEMBER, HASH_1);

        Optional<RefreshTokenRecord> found = store.findRefresh(HASH_1);

        assertThat(found).hasValueSatisfying(record -> {
            assertThat(record.sessionId()).isEqualTo(SID);
            assertThat(record.memberId()).isEqualTo(MEMBER);
            assertThat(record.expiresAt()).isEqualTo(exp);
            assertThat(record.current()).isTrue();
        });
    }

    @Test
    void findRefreshIsEmptyForUnknownHashOrMissingSession() {
        assertThat(store.findRefresh(HASH_1)).isEmpty();

        createSession(SID, MEMBER, HASH_1);
        redisTemplate.delete("auth:session:" + SID);

        assertThat(store.findRefresh(HASH_1)).isEmpty();
    }

    @Test
    void findRefreshIsEmptyWhenSessionBelongsToAnotherMember() {
        createSession(SID, MEMBER, HASH_1);
        redisTemplate.opsForHash().put("auth:session:" + SID, "memberId", "7");

        assertThat(store.findRefresh(HASH_1)).isEmpty();
    }

    // ----- rotate -----

    @Test
    void rotationKeepsSessionIdAndAbsoluteExpiryAndLeavesOldHashAsUsed() { // MB-T035
        Instant exp = createSession(SID, MEMBER, HASH_1);

        RotateResult result = store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp));

        assertThat(result).isEqualTo(RotateResult.ROTATED);
        assertThat(redisTemplate.opsForHash().get("auth:session:" + SID, "refreshHash")).isEqualTo(HASH_2);
        Map<Object, Object> newRefresh = redisTemplate.opsForHash().entries("auth:refresh:" + HASH_2);
        assertThat(newRefresh).containsEntry("sid", SID).containsEntry("memberId", "42")
                .containsEntry("expiresAt", Long.toString(exp.toEpochMilli()));
        // 세션·새 Refresh·이전 Refresh 모두 최초 절대 만료 시각 그대로(PEXPIRETIME 정확값)
        assertThat(absoluteExpiry("auth:session:" + SID)).isEqualTo(exp.toEpochMilli());
        assertThat(absoluteExpiry("auth:refresh:" + HASH_2)).isEqualTo(exp.toEpochMilli());
        assertThat(absoluteExpiry("auth:refresh:" + HASH_1)).isEqualTo(exp.toEpochMilli());
        assertThat(store.findRefresh(HASH_1)).hasValueSatisfying(r -> assertThat(r.current()).isFalse());
        assertThat(store.findRefresh(HASH_2)).hasValueSatisfying(r -> assertThat(r.current()).isTrue());
        assertThat(store.verify(SID, MEMBER)).isEqualTo(LoginSessionStatus.ACTIVE); // 기존 Access는 계속 유효
    }

    @Test
    void repeatedRotationNeverExtendsExpiryAndOldUsedTokenIsReuse() { // MB-T035·MB-T036
        Instant exp = createSession(SID, MEMBER, HASH_1);
        assertThat(store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp))).isEqualTo(RotateResult.ROTATED);
        assertThat(store.rotate(new RefreshRotation(SID, MEMBER, HASH_2, HASH_3, exp))).isEqualTo(RotateResult.ROTATED);
        assertThat(absoluteExpiry("auth:session:" + SID)).isEqualTo(exp.toEpochMilli());
        assertThat(absoluteExpiry("auth:refresh:" + HASH_3)).isEqualTo(exp.toEpochMilli());

        // 두 번 전의(오래된) USED 토큰
        RotateResult reuse = store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, "4".repeat(64), exp));

        assertThat(reuse).isEqualTo(RotateResult.REUSED);
        assertThat(redisTemplate.hasKey("auth:session:" + SID)).isFalse();
        assertThat(redisTemplate.hasKey("auth:refresh:" + HASH_3)).isFalse(); // 최신 Refresh도 더는 쓸 수 없다
        assertThat(redisTemplate.hasKey("auth:refresh:" + "4".repeat(64))).isFalse();
        assertThat(store.findRefresh(HASH_3)).isEmpty();
        assertThat(store.verify(SID, MEMBER)).isEqualTo(LoginSessionStatus.NOT_FOUND); // 최신 Access도 거부
    }

    @Test
    void reuseRevokesOnlyThatSessionNotOtherSessionsOfTheSameMember() { // MB-T036
        Instant exp = createSession(SID, MEMBER, HASH_1);
        createSession(OTHER_SID, MEMBER, OTHER_HASH);
        store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp));

        store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_3, exp));

        assertThat(store.verify(SID, MEMBER)).isEqualTo(LoginSessionStatus.NOT_FOUND);
        assertThat(store.verify(OTHER_SID, MEMBER)).isEqualTo(LoginSessionStatus.ACTIVE);
        assertThat(store.findRefresh(OTHER_HASH)).hasValueSatisfying(r -> assertThat(r.current()).isTrue());
    }

    @Test
    void newHashCollisionChangesNothing() { // MB-T035
        Instant exp = createSession(SID, MEMBER, HASH_1);
        createSession(OTHER_SID, MEMBER, OTHER_HASH);

        RotateResult result = store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, OTHER_HASH, exp));

        assertThat(result).isEqualTo(RotateResult.COLLISION);
        assertThat(redisTemplate.opsForHash().get("auth:session:" + SID, "refreshHash")).isEqualTo(HASH_1);
        assertThat(redisTemplate.opsForHash().get("auth:refresh:" + OTHER_HASH, "sid")).isEqualTo(OTHER_SID);
        assertThat(store.findRefresh(HASH_1)).hasValueSatisfying(r -> assertThat(r.current()).isTrue());
    }

    @Test
    void rotationOfRevokedSessionIsInvalidAndNeverRecreatesTheSession() { // MB-T037
        Instant exp = createSession(SID, MEMBER, HASH_1);
        assertThat(store.revoke(SID, MEMBER)).isEqualTo(RevokeResult.REVOKED);

        RotateResult result = store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp));

        assertThat(result).isEqualTo(RotateResult.INVALID);
        assertThat(redisTemplate.hasKey("auth:session:" + SID)).isFalse();
        assertThat(redisTemplate.hasKey("auth:refresh:" + HASH_2)).isFalse();
    }

    @Test
    void rotationChecksSidMemberAndExpiryRelationsBeforeWriting() { // MB-T035
        Instant exp = createSession(SID, MEMBER, HASH_1);
        createSession(OTHER_SID, MEMBER, OTHER_HASH);

        assertThat(store.rotate(new RefreshRotation(OTHER_SID, MEMBER, HASH_1, HASH_2, exp)))
                .as("다른 세션의 sid").isEqualTo(RotateResult.INVALID);
        assertThat(store.rotate(new RefreshRotation(SID, 7L, HASH_1, HASH_2, exp)))
                .as("다른 회원").isEqualTo(RotateResult.INVALID);
        assertThat(store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp.plusSeconds(1))))
                .as("절대 만료 시각 불일치").isEqualTo(RotateResult.INVALID);

        assertThat(redisTemplate.hasKey("auth:refresh:" + HASH_2)).isFalse();
        assertThat(redisTemplate.opsForHash().get("auth:session:" + SID, "refreshHash")).isEqualTo(HASH_1);
        assertThat(redisTemplate.opsForHash().get("auth:session:" + OTHER_SID, "refreshHash")).isEqualTo(OTHER_HASH);
    }

    @Test
    void rotationOfExpiredSessionIsInvalid() { // MB-T035
        Instant exp = createSession(SID, MEMBER, HASH_1);
        RedisLoginSessionStore afterExpiry = new RedisLoginSessionStore(redisTemplate,
                Clock.fixed(exp.plusMillis(1), java.time.ZoneOffset.UTC));

        assertThat(afterExpiry.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp)))
                .isEqualTo(RotateResult.INVALID);
        assertThat(redisTemplate.hasKey("auth:refresh:" + HASH_2)).isFalse();
    }

    @Test
    void wrongTypeOldRefreshKeyIsInvalidWithoutWriting() { // 쓰기 전 타입 검증
        Instant exp = createSession(SID, MEMBER, HASH_1);
        redisTemplate.delete("auth:refresh:" + HASH_1);
        redisTemplate.opsForValue().set("auth:refresh:" + HASH_1, "string");

        assertThat(store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp))).isEqualTo(RotateResult.INVALID);
        assertThat(redisTemplate.hasKey("auth:refresh:" + HASH_2)).isFalse();
        assertThat(redisTemplate.opsForHash().get("auth:session:" + SID, "refreshHash")).isEqualTo(HASH_1);
    }

    @Test
    void concurrentRotationsWithTheSameTokenLetAtMostOneSucceedAndReuseRevokes() throws Exception { // MB-T037
        Instant exp = createSession(SID, MEMBER, HASH_1);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RotateResult>> futures = new ArrayList<>();
        List<String> newHashes = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String newHash = String.format("%064x", i + 100);
                newHashes.add(newHash);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, newHash, exp));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<RotateResult> results = new ArrayList<>();
            for (Future<RotateResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(r -> r == RotateResult.ROTATED).hasSizeLessThanOrEqualTo(1);
            assertThat(results).filteredOn(r -> r == RotateResult.REUSED).isNotEmpty();
            // 재사용 탐지로 세션이 폐기됐고, 성공한 쪽의 새 Refresh도 쓸 수 없다(부분 저장 없음)
            assertThat(redisTemplate.hasKey("auth:session:" + SID)).isFalse();
            assertThat(redisTemplate.keys("auth:refresh:*")).allSatisfy(key ->
                    assertThat(store.findRefresh(key.substring("auth:refresh:".length()))).isEmpty());
            // 고아 키 없음: 각 작업이 제출한 새 해시 키가 남지 않는다(최초 발급 HASH_1은 USED로 만료까지 남을 수 있어 제외)
            assertThat(newHashes).allSatisfy(hash ->
                    assertThat(redisTemplate.hasKey("auth:refresh:" + hash)).as(hash).isFalse());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void logoutCompletingBetweenLookupAndRotationPreventsRecreation() { // MB-T037
        createSession(SID, MEMBER, HASH_1);
        RefreshTokenRecord looked = store.findRefresh(HASH_1).orElseThrow();

        store.revoke(SID, MEMBER); // 조회 뒤 로그아웃이 먼저 끝남
        RotateResult result = store.rotate(new RefreshRotation(looked.sessionId(), looked.memberId(), HASH_1, HASH_2,
                looked.expiresAt()));

        assertThat(result).isEqualTo(RotateResult.INVALID);
        assertThat(redisTemplate.keys("auth:session:*")).isEmpty();
        assertThat(redisTemplate.hasKey("auth:refresh:" + HASH_2)).isFalse();
    }

    // ----- revoke -----

    @Test
    void revokeDeletesSessionAndItsCurrentRefreshOnlyForTheOwner() { // MB-T041
        createSession(SID, MEMBER, HASH_1);

        assertThat(store.revoke(SID, 7L)).isEqualTo(RevokeResult.MEMBER_MISMATCH);
        assertThat(redisTemplate.hasKey("auth:session:" + SID)).isTrue();

        assertThat(store.revoke(SID, MEMBER)).isEqualTo(RevokeResult.REVOKED);
        assertThat(redisTemplate.hasKey("auth:session:" + SID)).isFalse();
        assertThat(redisTemplate.hasKey("auth:refresh:" + HASH_1)).isFalse();

        assertThat(store.revoke(SID, MEMBER)).isEqualTo(RevokeResult.NOT_FOUND); // 멱등
    }

    @Test
    void revokeNeverDeletesARefreshKeyThatBelongsToAnotherSession() { // MB-T041
        createSession(SID, MEMBER, HASH_1);
        createSession(OTHER_SID, MEMBER, OTHER_HASH);
        // 비정상 상태: SID 세션의 refreshHash가 다른 세션의 Refresh 키를 가리킨다
        redisTemplate.opsForHash().put("auth:session:" + SID, "refreshHash", OTHER_HASH);

        store.revoke(SID, MEMBER);

        assertThat(redisTemplate.hasKey("auth:refresh:" + OTHER_HASH)).isTrue();
        assertThat(store.verify(OTHER_SID, MEMBER)).isEqualTo(LoginSessionStatus.ACTIVE);
    }

    @Test
    void usedRefreshStillLeadsToItsSessionForLogout() { // MB-T041
        Instant exp = createSession(SID, MEMBER, HASH_1);
        store.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp));

        RefreshTokenRecord used = store.findRefresh(HASH_1).orElseThrow();

        assertThat(used.current()).isFalse();
        assertThat(used.sessionId()).isEqualTo(SID);
        assertThat(store.revoke(used.sessionId(), used.memberId())).isEqualTo(RevokeResult.REVOKED);
        assertThat(redisTemplate.hasKey("auth:refresh:" + HASH_2)).isFalse();
    }

    // ----- 장애 -----

    @Test
    void unreachableRedisIsStoreUnavailableForEveryNewOperation() { // MB-T039
        try (RedisLoginSessionStoreTest.UnreachableRedis unreachable = RedisLoginSessionStoreTest.unreachableRedis()) {
            RedisLoginSessionStore broken = new RedisLoginSessionStore(unreachable.template(), Clock.systemUTC());
            Instant exp = Instant.now().plus(Duration.ofDays(1));

            assertThatThrownBy(() -> broken.findRefresh(HASH_1))
                    .isInstanceOf(LoginSessionStoreUnavailableException.class);
            assertThatThrownBy(() -> broken.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp)))
                    .isInstanceOf(LoginSessionStoreUnavailableException.class);
            assertThatThrownBy(() -> broken.revoke(SID, MEMBER))
                    .isInstanceOf(LoginSessionStoreUnavailableException.class);
        }
    }

    @Test
    void unknownScriptResultIsNotSilentlyTreatedAsInvalidOrNotFound() { // S6 지적: Lua·Java 반환 코드 불일치 방어
        // 스크립트가 정의하지 않은 코드: 9, 그리고 하위 32비트가 1인 값(intValue()로 자르면 1이 된다)
        for (long code : new long[] {9L, (1L << 32) + 1}) {
            StringRedisTemplate unexpected = org.mockito.Mockito.mock(StringRedisTemplate.class,
                    invocation -> "execute".equals(invocation.getMethod().getName()) ? code : null);
            RedisLoginSessionStore broken = new RedisLoginSessionStore(unexpected, Clock.systemUTC());
            Instant exp = Instant.now().plus(Duration.ofDays(1));

            assertThatThrownBy(() -> broken.rotate(new RefreshRotation(SID, MEMBER, HASH_1, HASH_2, exp)))
                    .as("rotate code=%d", code).isInstanceOf(LoginSessionStoreUnavailableException.class);
            assertThatThrownBy(() -> broken.revoke(SID, MEMBER))
                    .as("revoke code=%d", code).isInstanceOf(LoginSessionStoreUnavailableException.class);
        }
    }

    private long absoluteExpiry(String key) {
        return redisTemplate.execute(new DefaultRedisScript<>("return redis.call('PEXPIRETIME', KEYS[1])", Long.class),
                List.of(key));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
