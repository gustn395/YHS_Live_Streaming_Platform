package com.yhs.livestreaming.member.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties;
import com.yhs.livestreaming.member.app.otp.NewOtpChallenge;
import com.yhs.livestreaming.member.app.otp.OtpChallengeSnapshot;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStore.DeliveryStage;
import com.yhs.livestreaming.member.app.otp.OtpStoreResult;
import com.yhs.livestreaming.member.app.otp.OtpStoreResult.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * MB-T023·MB-T024·MB-T025·MB-T026(저장소): OTP Challenge 생명주기(MB-D015~D017). Lua로 제한 검사·카운터·TTL·검증·소비를
 * 원자 처리하고, version으로 재발송·검증·발송 실패 보상의 경합을 구분한다.
 */
@DataRedisTest
class RedisOtpChallengeStoreTest extends RedisTestSupport {

    private static final long MEMBER = 42L;
    private static final String KEY_ID = "otp-key-1";

    @Autowired
    private StringRedisTemplate template;

    private RedisOtpChallengeStore store;
    private Instant now;

    @BeforeEach
    void setUp() {
        store = new RedisOtpChallengeStore(template, MemberLimitProperties.defaults(), java.time.Clock.systemUTC());
        now = Instant.now();
    }

    @Test
    void issueStoresChallengeHashPointerCountersAndInterval() {
        OtpStoreResult result = store.issueForLogin(challenge("ch-1", "hmac-1"), now);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.version()).isEqualTo(1);
        Map<Object, Object> hash = template.opsForHash().entries("auth:otp:challenge:ch-1");
        assertThat(hash).containsEntry("memberId", "42").containsEntry("version", "1")
                .containsEntry("otpHmac", "hmac-1").containsEntry("keyId", KEY_ID).containsEntry("attempts", "0");
        assertThat(template.opsForValue().get("auth:otp:member:42:current")).isEqualTo("ch-1");
        assertThat(template.opsForValue().get("auth:otp:member:42:issue:1h")).isEqualTo("1");
        assertThat(template.opsForValue().get("auth:otp:member:42:issue:24h")).isEqualTo("1");
        assertThat(template.getExpire("auth:otp:member:42:last-issued")).isBetween(1L, 60L);
        assertThat(template.getExpire("auth:otp:challenge:ch-1")).isBetween(590L, 600L);
    }

    @Test
    void secondLoginWithinSixtySecondsIsLimitedAndKeepsTheFirstChallenge() {
        issueConfirmed("ch-1", "hmac-1");

        OtpStoreResult second = store.issueForLogin(challenge("ch-2", "hmac-2"), now);

        assertThat(second.status()).isEqualTo(Status.INTERVAL_LIMITED);
        assertThat(second.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(60));
        assertThat(template.hasKey("auth:otp:challenge:ch-1")).isTrue();
        assertThat(template.hasKey("auth:otp:challenge:ch-2")).isFalse();
        assertThat(template.opsForValue().get("auth:otp:member:42:issue:1h")).isEqualTo("1");
    }

    @Test
    void newLoginAfterTheIntervalReplacesThePreviousChallenge() {
        issueConfirmed("ch-1", "hmac-1");
        passInterval();

        OtpStoreResult second = store.issueForLogin(challenge("ch-2", "hmac-2"), now);

        assertThat(second.status()).isEqualTo(Status.OK);
        assertThat(template.hasKey("auth:otp:challenge:ch-1")).isFalse();
        assertThat(template.opsForValue().get("auth:otp:member:42:current")).isEqualTo("ch-2");
        assertThat(store.find("ch-1")).isEmpty();
        assertThat(store.verify(snapshot("ch-1", 1), "hmac-1", now).status()).isEqualTo(Status.INVALID_CHALLENGE);
    }

    @Test
    void fivePerHourAcrossNewLoginsAndResendsThenLimited() {
        issueConfirmed("ch-1", "h");
        for (int i = 2; i <= 5; i++) {
            passInterval();
            OtpChallengeSnapshot current = store.find("ch-1").orElseThrow();
            assertThat(store.resend(current, "h" + i, "digest-" + "h" + i, KEY_ID, now.plusSeconds(300), now).status()).isEqualTo(Status.OK);
        }
        passInterval();

        OtpStoreResult sixth = store.issueForLogin(challenge("ch-9", "h9"), now);

        assertThat(sixth.status()).isEqualTo(Status.ISSUE_LIMITED);
        assertThat(sixth.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofHours(1));
    }

    @Test
    void twentyPerDayLimit() {
        template.opsForValue().set("auth:otp:member:42:issue:24h", "20", Duration.ofHours(5));

        OtpStoreResult result = store.issueForLogin(challenge("ch-1", "h"), now);

        assertThat(result.status()).isEqualTo(Status.ISSUE_LIMITED);
        assertThat(result.retryAfter()).isLessThanOrEqualTo(Duration.ofHours(5));
    }

    @Test
    void verifySuccessConsumesChallengeAtomically() {
        issueConfirmed("ch-1", "hmac-1");

        OtpStoreResult ok = store.verify(snapshot("ch-1", 1), "hmac-1", now);

        assertThat(ok.status()).isEqualTo(Status.OK);
        assertThat(ok.memberId()).isEqualTo(MEMBER);
        assertThat(template.hasKey("auth:otp:challenge:ch-1")).isFalse();
        assertThat(template.hasKey("auth:otp:member:42:current")).isFalse();
        assertThat(store.verify(snapshot("ch-1", 1), "hmac-1", now).status()).isEqualTo(Status.INVALID_CHALLENGE);
    }

    @Test
    void fiveMismatchesExhaustTheOtpAndCountTowardTheDailyFailureSum() {
        issueConfirmed("ch-1", "hmac-1");
        for (int i = 1; i <= 4; i++) {
            assertThat(store.verify(snapshot("ch-1", 1), "wrong", now).status()).isEqualTo(Status.MISMATCH);
        }

        assertThat(store.verify(snapshot("ch-1", 1), "wrong", now).status()).isEqualTo(Status.EXHAUSTED);

        assertThat(template.opsForHash().get("auth:otp:challenge:ch-1", "otpHmac")).isEqualTo("");
        assertThat(template.opsForValue().get("auth:otp:member:42:fail:24h")).isEqualTo("5");
        assertThat(store.verify(snapshot("ch-1", 1), "hmac-1", now).status()).isEqualTo(Status.EXPIRED);
    }

    @Test
    void expiredOtpIsRejectedWithoutCountingAFailure() {
        issueConfirmed("ch-1", "hmac-1");

        OtpStoreResult late = store.verify(snapshot("ch-1", 1), "hmac-1", now.plus(Duration.ofMinutes(5)));

        assertThat(late.status()).isEqualTo(Status.EXPIRED);
        assertThat(template.hasKey("auth:otp:member:42:fail:24h")).isFalse();
    }

    @Test
    void challengePastItsAbsoluteExpiryIsInvalid() {
        issueConfirmed("ch-1", "hmac-1");

        assertThat(store.verify(snapshot("ch-1", 1), "hmac-1", now.plus(Duration.ofMinutes(10))).status())
                .isEqualTo(Status.INVALID_CHALLENGE);
    }

    @Test
    void tenFailuresAcrossChallengesBlockVerifyAndNewIssues() {
        template.opsForValue().set("auth:otp:member:42:fail:24h", "10", Duration.ofHours(3));
        template.opsForHash().putAll("auth:otp:challenge:ch-1", Map.of("memberId", "42", "version", "1",
                "otpHmac", "hmac-1", "keyId", KEY_ID, "attempts", "0",
                "otpExpiresAt", Long.toString(now.plusSeconds(300).toEpochMilli()),
                "challengeExpiresAt", Long.toString(now.plusSeconds(600).toEpochMilli())));
        template.opsForValue().set("auth:otp:member:42:current", "ch-1");

        OtpStoreResult verify = store.verify(snapshot("ch-1", 1), "hmac-1", now);
        OtpStoreResult issue = store.issueForLogin(challenge("ch-2", "hmac-2"), now);

        assertThat(verify.status()).isEqualTo(Status.FAILURE_LIMITED);
        assertThat(verify.retryAfter()).isLessThanOrEqualTo(Duration.ofHours(3));
        assertThat(issue.status()).isEqualTo(Status.FAILURE_LIMITED);
    }

    @Test
    void newChallengeDoesNotResetMemberCounters() {
        issueConfirmed("ch-1", "hmac-1");
        store.verify(snapshot("ch-1", 1), "wrong", now);
        passInterval();

        issueConfirmed("ch-2", "hmac-2");

        assertThat(template.opsForValue().get("auth:otp:member:42:fail:24h")).isEqualTo("1");
        assertThat(template.opsForValue().get("auth:otp:member:42:issue:1h")).isEqualTo("2");
    }

    @Test
    void resendBumpsVersionResetsAttemptsKeepsChallengeExpiryAndInvalidatesPreviousOtp() {
        issueConfirmed("ch-1", "hmac-1");
        store.verify(snapshot("ch-1", 1), "wrong", now);
        Long challengeTtl = template.getExpire("auth:otp:challenge:ch-1");
        passInterval();

        OtpStoreResult resent = store.resend(store.find("ch-1").orElseThrow(), "hmac-2", "digest-" + "hmac-2", KEY_ID,
                now.plusSeconds(300), now);

        assertThat(resent.status()).isEqualTo(Status.OK);
        assertThat(resent.version()).isEqualTo(2);
        Map<Object, Object> hash = template.opsForHash().entries("auth:otp:challenge:ch-1");
        assertThat(hash).containsEntry("version", "2").containsEntry("attempts", "0").containsEntry("otpHmac", "hmac-2");
        assertThat(template.getExpire("auth:otp:challenge:ch-1")).isLessThanOrEqualTo(challengeTtl);
        assertThat(store.confirmDelivered("ch-1", 2)).isTrue();
        assertThat(store.verify(snapshot("ch-1", 2), "hmac-1", now).status()).isEqualTo(Status.MISMATCH);
    }

    @Test
    void otpCannotBeVerifiedUntilItsDeliveryIsConfirmed() { // MB-D016: 발송 성공 확인 전 검증 불가
        store.issueForLogin(challenge("ch-1", "hmac-1"), now);

        assertThat(store.verify(snapshot("ch-1", 1), "hmac-1", now).status()).isEqualTo(Status.EXPIRED);
        assertThat(template.hasKey("auth:otp:member:42:fail:24h")).isFalse(); // 실패로 세지 않는다
        assertThat(template.hasKey("auth:otp:challenge:ch-1")).isTrue();

        assertThat(store.confirmDelivered("ch-1", 1)).isTrue();
        assertThat(store.verify(snapshot("ch-1", 1), "hmac-1", now).status()).isEqualTo(Status.OK);
    }

    @Test
    void resentOtpIsUnusableWhenDeliveryOutcomeIsUnknownAndCompensationNeverRan() {
        // SMTP가 메일을 받았지만 응답 시간 초과, 보상도 Redis 장애로 실패, 이후 Redis 복구: 확인되지 않은 OTP는 통과하지 않는다
        issueConfirmed("ch-1", "hmac-1");
        passInterval();
        store.resend(store.find("ch-1").orElseThrow(), "hmac-2", "digest-hmac-2", KEY_ID, now.plusSeconds(300), now);

        assertThat(store.verify(store.find("ch-1").orElseThrow(), "hmac-2", now).status()).isEqualTo(Status.EXPIRED);
        assertThat(store.verify(store.find("ch-1").orElseThrow(), "hmac-1", now).status()).isEqualTo(Status.EXPIRED);
    }

    @Test
    void confirmationOfAnOlderVersionDoesNotActivateTheNewerOtp() {
        issueConfirmed("ch-1", "hmac-1");
        passInterval();
        store.resend(store.find("ch-1").orElseThrow(), "hmac-2", "digest-hmac-2", KEY_ID, now.plusSeconds(300), now);

        assertThat(store.confirmDelivered("ch-1", 1)).isFalse();
        assertThat(store.verify(snapshot("ch-1", 2), "hmac-2", now).status()).isEqualTo(Status.EXPIRED);
        assertThat(store.confirmDelivered("missing", 1)).isFalse();
    }

    @Test
    void resendWithinSixtySecondsIsLimited() {
        issueConfirmed("ch-1", "hmac-1");

        OtpStoreResult resent = store.resend(store.find("ch-1").orElseThrow(), "hmac-2", "digest-" + "hmac-2", KEY_ID,
                now.plusSeconds(300), now);

        assertThat(resent.status()).isEqualTo(Status.INTERVAL_LIMITED);
        assertThat(template.opsForHash().get("auth:otp:challenge:ch-1", "otpHmac")).isEqualTo("hmac-1");
    }

    @Test
    void verifyComputedForAnOlderVersionIsAConflictAndIsNotCountedAsFailure() {
        issueConfirmed("ch-1", "hmac-1");
        OtpChallengeSnapshot beforeResend = store.find("ch-1").orElseThrow();
        passInterval();
        store.resend(beforeResend, "hmac-2", "digest-" + "hmac-2", KEY_ID, now.plusSeconds(300), now);

        OtpStoreResult raced = store.verify(beforeResend, "hmac-1", now);

        assertThat(raced.status()).isEqualTo(Status.VERSION_CONFLICT);
        assertThat(template.hasKey("auth:otp:member:42:fail:24h")).isFalse();
        assertThat(template.opsForHash().get("auth:otp:challenge:ch-1", "attempts")).isEqualTo("0");
    }

    @Test
    void resendFromAStaleSnapshotIsAConflict() {
        issueConfirmed("ch-1", "hmac-1");
        OtpChallengeSnapshot stale = store.find("ch-1").orElseThrow();
        passInterval();
        store.resend(stale, "hmac-2", "digest-" + "hmac-2", KEY_ID, now.plusSeconds(300), now);
        passInterval();

        assertThat(store.resend(stale, "hmac-3", "digest-" + "hmac-3", KEY_ID, now.plusSeconds(300), now).status())
                .isEqualTo(Status.VERSION_CONFLICT);
    }

    @Test
    void loginDeliveryFailureRemovesOnlyTheMatchingVersion() {
        issueConfirmed("ch-1", "hmac-1");

        assertThat(store.deliveryFailed("ch-1", 2, DeliveryStage.LOGIN)).isFalse();
        assertThat(template.hasKey("auth:otp:challenge:ch-1")).isTrue();

        assertThat(store.deliveryFailed("ch-1", 1, DeliveryStage.LOGIN)).isTrue();
        assertThat(template.hasKey("auth:otp:challenge:ch-1")).isFalse();
        assertThat(template.hasKey("auth:otp:member:42:current")).isFalse();
        // 카운터와 60초 간격은 되돌리지 않는다
        assertThat(template.opsForValue().get("auth:otp:member:42:issue:1h")).isEqualTo("1");
        assertThat(template.hasKey("auth:otp:member:42:last-issued")).isTrue();
    }

    @Test
    void staleLoginDeliveryFailureDoesNotTouchANewerChallenge() {
        issueConfirmed("ch-1", "hmac-1");
        passInterval();
        issueConfirmed("ch-2", "hmac-2");

        assertThat(store.deliveryFailed("ch-1", 1, DeliveryStage.LOGIN)).isFalse();
        assertThat(template.opsForValue().get("auth:otp:member:42:current")).isEqualTo("ch-2");
        assertThat(template.hasKey("auth:otp:challenge:ch-2")).isTrue();
    }

    @Test
    void resendDeliveryFailureInvalidatesOnlyThatVersionAndKeepsTheChallenge() {
        issueConfirmed("ch-1", "hmac-1");
        passInterval();
        store.resend(store.find("ch-1").orElseThrow(), "hmac-2", "digest-" + "hmac-2", KEY_ID, now.plusSeconds(300), now);

        assertThat(store.deliveryFailed("ch-1", 1, DeliveryStage.RESEND)).isFalse();
        assertThat(template.opsForHash().get("auth:otp:challenge:ch-1", "otpHmac")).isEqualTo("hmac-2");

        assertThat(store.deliveryFailed("ch-1", 2, DeliveryStage.RESEND)).isTrue();
        assertThat(template.opsForHash().get("auth:otp:challenge:ch-1", "otpHmac")).isEqualTo("");
        assertThat(store.find("ch-1")).isPresent();
        assertThat(store.verify(snapshot("ch-1", 2), "hmac-2", now).status()).isEqualTo(Status.EXPIRED);
    }

    @Test
    void recentSentDigestsKeepBothTheFailedAndThePreviousCode() { // 실패한 메일도 실제로 전달됐을 수 있다
        issueConfirmed("ch-1", "hmac-1");
        assertThat(store.find("ch-1").orElseThrow().recentDigests()).containsExactly("digest-hmac-1");
        passInterval();
        store.resend(store.find("ch-1").orElseThrow(), "hmac-2", "digest-hmac-2", KEY_ID, now.plusSeconds(300), now);

        store.deliveryFailed("ch-1", 2, DeliveryStage.RESEND); // 타임아웃: 전달 여부를 모른다

        assertThat(store.find("ch-1").orElseThrow().recentDigests())
                .containsExactlyInAnyOrder("digest-hmac-2", "digest-hmac-1");
    }

    @Test
    void findUsesTheInjectedClockForChallengeExpiry() {
        issueConfirmed("ch-1", "hmac-1");
        RedisOtpChallengeStore later = new RedisOtpChallengeStore(template, MemberLimitProperties.defaults(),
                java.time.Clock.fixed(now.plusSeconds(600), java.time.ZoneOffset.UTC));

        assertThat(store.find("ch-1")).isPresent();
        assertThat(later.find("ch-1")).isEmpty();
    }

    @Test
    void concurrentLoginsForOneMemberIssueExactlyOneChallenge() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<OtpStoreResult>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String id = "ch-" + i;
                calls.add(() -> store.issueForLogin(challenge(id, "h"), now));
            }
            long ok = 0;
            for (Future<OtpStoreResult> result : pool.invokeAll(calls)) {
                Status status = result.get().status();
                if (status == Status.OK) {
                    ok++;
                } else {
                    assertThat(status).isEqualTo(Status.INTERVAL_LIMITED);
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(template.keys("auth:otp:challenge:*")).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unreachableRedisIsStoreUnavailable() {
        try (RedisLoginSessionStoreTest.UnreachableRedis unreachable = RedisLoginSessionStoreTest.unreachableRedis()) {
            RedisOtpChallengeStore broken = new RedisOtpChallengeStore(unreachable.template(),
                    MemberLimitProperties.defaults(), java.time.Clock.systemUTC());

            assertThatThrownBy(() -> broken.issueForLogin(challenge("ch-1", "h"), now))
                    .isInstanceOf(LoginSessionStoreUnavailableException.class);
            assertThatThrownBy(() -> broken.find("ch-1")).isInstanceOf(LoginSessionStoreUnavailableException.class);
        }
    }

    @Test
    void issueStoresTheDeliveryEmailAndFindReturnsIt() { // MB-T053
        issueConfirmed("ch-mail", "h1");

        assertThat(store.find("ch-mail")).hasValueSatisfying(
                snapshot -> assertThat(snapshot.deliveryEmail()).isEqualTo("deliver@example.com"));
    }

    @Test
    void legacyChallengeWithoutDeliveryEmailIsNotFound() { // MB-T053: 필드 없는 이전 Challenge는 무효
        issueConfirmed("ch-old", "h1");
        template.opsForHash().delete("auth:otp:challenge:ch-old", "deliveryEmail");

        assertThat(store.find("ch-old")).isEmpty();
    }

    /** 발급 + 메일 발송 성공 확인까지 끝낸 상태(검증 가능). */
    private void issueConfirmed(String id, String hmac) {
        assertThat(store.issueForLogin(challenge(id, hmac), now).status()).isEqualTo(Status.OK);
        assertThat(store.confirmDelivered(id, 1)).isTrue();
    }

    private NewOtpChallenge challenge(String id, String hmac) {
        return new NewOtpChallenge(id, MEMBER, hmac, "digest-" + hmac, KEY_ID, now.plusSeconds(300), now.plusSeconds(600),
                "deliver@example.com");
    }

    private OtpChallengeSnapshot snapshot(String id, int version) {
        return new OtpChallengeSnapshot(id, MEMBER, version, KEY_ID, now.plusSeconds(300), now.plusSeconds(600), List.of(),
                "deliver@example.com");
    }

    private void passInterval() {
        template.delete("auth:otp:member:42:last-issued");
    }
}
