package com.yhs.livestreaming.member.app.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.TooManyRequestsException;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.otp.LoginChallenge;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStore.DeliveryStage;
import com.yhs.livestreaming.member.app.otp.OtpStoreResult.Status;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MB-T021·MB-T023~MB-T026(서비스): OTP 발급·재발송·검증과 발송 실패 보상(MB-D015, MB-D016). 저장소·메일은 대역이다.
 */
@ExtendWith(OutputCaptureExtension.class)
class OtpServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final Duration INTERVAL = Duration.ofSeconds(45);
    static final String OTP_KEY = "Zm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyMTI="; // 테스트 전용 32바이트

    private final FakeStore store = new FakeStore();
    private final List<String[]> sent = new ArrayList<>();
    private RuntimeException mailFailure;
    private final Deque<Integer> randoms = new ArrayDeque<>(List.of(1234, 1234, 5678, 9012));
    private final OtpCodes codes = new OtpCodes(new MemberOtpProperties(OTP_KEY, "otp-key-1"), () -> randoms.pop());
    private final Member member = member();
    private final OtpService service = new OtpService(store, codes, (email, code, expiresAt) -> {
        if (mailFailure != null) {
            throw mailFailure;
        }
        sent.add(new String[] {email, code});
    }, id -> id == 42L ? Optional.of(member) : Optional.empty(), Clock.fixed(NOW, ZoneOffset.UTC),
            () -> "challenge-" + store.issued.size(), INTERVAL);

    @Test
    void startStoresOnlyHmacWithFiveAndTenMinuteExpiriesAndMailsTheCode() {
        LoginChallenge challenge = service.startForLogin(member);

        NewOtpChallenge stored = store.issued.get(0);
        assertThat(stored.otpHmac()).isEqualTo(codes.hmac(stored.challengeId(), 1, "1234")).doesNotContain("1234");
        assertThat(stored.codeDigest()).isEqualTo(codes.digest(stored.challengeId(), "1234"))
                .isNotEqualTo(stored.otpHmac());
        assertThat(stored.otpExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(stored.challengeExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).containsExactly("tester@example.com", "1234");
        assertThat(challenge.challengeId()).isEqualTo(stored.challengeId());
        assertThat(challenge.otpExpiresAt()).isEqualTo(stored.otpExpiresAt());
    }

    @Test
    void codesAreFourDigitsWithLeadingZeros() {
        OtpCodes zeroes = new OtpCodes(new MemberOtpProperties(OTP_KEY, "otp-key-1"), () -> 7);

        assertThat(zeroes.newCode()).isEqualTo("0007");
    }

    @Test
    void issueLimitIsTooManyRequestsWithoutMail() {
        store.next = new OtpStoreResult(Status.INTERVAL_LIMITED, 0, 0, Duration.ofSeconds(42));

        assertThatThrownBy(() -> service.startForLogin(member))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.OTP_RATE_LIMITED);
                    assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(42));
                });
        assertThat(sent).isEmpty();
    }

    @Test
    void loginMailFailureCompensatesThatVersionAndHidesDetails(CapturedOutput output) {
        mailFailure = new IllegalStateException("smtp said no to tester@example.com about 1234");

        assertThat(failure(() -> service.startForLogin(member)).errorCode())
                .isEqualTo(MemberErrorCode.OTP_DELIVERY_UNAVAILABLE);
        assertThat(store.compensated).containsExactly(store.issued.get(0).challengeId() + "#1#LOGIN");
        assertThat(output).doesNotContain("tester@example.com").doesNotContain("1234").doesNotContain("smtp said");
    }

    @Test
    void resendBumpsVersionCapsExpiryAtChallengeAndNeverRepeatsTheDeliveredCode() {
        String id = "challenge-x";
        store.snapshot = new OtpChallengeSnapshot(id, 42L, 1, "otp-key-1", NOW.plusSeconds(30),
                NOW.plusSeconds(120), codes.digest(id, "1234"));

        OtpResent resent = service.resend(id);

        assertThat(store.resent).hasSize(1);
        assertThat(store.resent.get(0)).isEqualTo(codes.hmac(id, 2, "5678"));
        assertThat(store.resentDigests).containsExactly(codes.digest(id, "5678"));
        assertThat(resent.otpExpiresAt()).isEqualTo(NOW.plusSeconds(120)); // min(now+5분, Challenge 만료)
        assertThat(sent.get(0)[1]).isEqualTo("5678");
    }

    @Test
    void resendOfUnknownChallengeAsksForNewLogin() {
        assertThat(failure(() -> service.resend("nope")).errorCode())
                .isEqualTo(MemberErrorCode.OTP_CHALLENGE_INVALID);
    }

    @Test
    void resendMailFailureInvalidatesOnlyTheNewVersion() {
        store.snapshot = new OtpChallengeSnapshot("c", 42L, 3, "otp-key-1", NOW.plusSeconds(30),
                NOW.plusSeconds(500), "");
        mailFailure = new IllegalStateException("boom");

        assertThat(failure(() -> service.resend("c")).errorCode())
                .isEqualTo(MemberErrorCode.OTP_DELIVERY_UNAVAILABLE);
        assertThat(store.compensated).containsExactly("c#4#RESEND");
    }

    @Test
    void otpBecomesUsableOnlyAfterSmtpAcceptedTheMail() { // MB-D016
        LoginChallenge challenge = service.startForLogin(member);

        assertThat(store.confirmed).containsExactly(challenge.challengeId() + "#1");
    }

    @Test
    void failedDeliveryIsNeverConfirmed() {
        mailFailure = new IllegalStateException("timeout");

        failure(() -> service.startForLogin(member));

        assertThat(store.confirmed).isEmpty();
    }

    @Test
    void confirmationThatNoLongerMatchesIsTreatedAsDeliveryFailure() {
        store.confirmResult = false;

        assertThat(failure(() -> service.startForLogin(member)).errorCode())
                .isEqualTo(MemberErrorCode.OTP_DELIVERY_UNAVAILABLE);
    }

    @Test
    void resendAvoidsBothRecentlySentCodes() {
        String id = "challenge-x";
        randoms.clear();
        randoms.addAll(List.of(1234, 5678, 9012));
        store.snapshot = new OtpChallengeSnapshot(id, 42L, 2, "otp-key-1", NOW.plusSeconds(30),
                NOW.plusSeconds(120), List.of(codes.digest(id, "5678"), codes.digest(id, "1234")));

        service.resend(id);

        assertThat(sent.get(0)[1]).isEqualTo("9012");
        assertThat(store.confirmed).containsExactly(id + "#3");
    }

    @Test
    void compensationFailureStillReportsDeliveryUnavailable(CapturedOutput output) {
        mailFailure = new IllegalStateException("boom");
        store.compensationFailure = new com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException(
                "redis down", null);

        assertThat(failure(() -> service.startForLogin(member)).errorCode())
                .isEqualTo(MemberErrorCode.OTP_DELIVERY_UNAVAILABLE);
        assertThat(output).contains("compensated=false");
    }

    @Test
    void racingResendWaitsForTheConfiguredInterval() {
        store.snapshot = new OtpChallengeSnapshot("c", 42L, 1, "otp-key-1", NOW.plusSeconds(30),
                NOW.plusSeconds(500), "");
        store.next = OtpStoreResult.of(Status.VERSION_CONFLICT);

        assertThatThrownBy(() -> service.resend("c"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.retryAfter()).isEqualTo(INTERVAL));
    }

    @Test
    void verifyComputesHmacWithTheCurrentVersionAndReturnsMember() {
        store.snapshot = new OtpChallengeSnapshot("c", 42L, 2, "otp-key-1", NOW.plusSeconds(30),
                NOW.plusSeconds(500), "");
        store.next = new OtpStoreResult(Status.OK, 2, 42L, Duration.ZERO);

        assertThat(service.verify("c", "0420")).isEqualTo(42L);
        assertThat(store.verified).containsExactly(codes.hmac("c", 2, "0420"));
    }

    @Test
    void verifyOutcomesMapToDistinctCodes() {
        store.snapshot = new OtpChallengeSnapshot("c", 42L, 1, "otp-key-1", NOW.plusSeconds(30),
                NOW.plusSeconds(500), "");
        assertThat(verifyError(Status.MISMATCH)).isEqualTo(MemberErrorCode.OTP_MISMATCH);
        assertThat(verifyError(Status.EXPIRED)).isEqualTo(MemberErrorCode.OTP_EXPIRED);
        assertThat(verifyError(Status.EXHAUSTED)).isEqualTo(MemberErrorCode.OTP_EXPIRED);
        assertThat(verifyError(Status.VERSION_CONFLICT)).isEqualTo(MemberErrorCode.OTP_EXPIRED);
        assertThat(verifyError(Status.INVALID_CHALLENGE)).isEqualTo(MemberErrorCode.OTP_CHALLENGE_INVALID);
        assertThat(verifyError(Status.FAILURE_LIMITED)).isEqualTo(MemberErrorCode.OTP_RATE_LIMITED);
    }

    @Test
    void exhaustedAndTimeExpiredHaveDifferentGuidance() {
        store.snapshot = new OtpChallengeSnapshot("c", 42L, 1, "otp-key-1", NOW.plusSeconds(30),
                NOW.plusSeconds(500), "");
        store.next = OtpStoreResult.of(Status.EXHAUSTED);
        String exhausted = failure(() -> service.verify("c", "0000")).getMessage();
        store.next = OtpStoreResult.of(Status.EXPIRED);
        String expired = failure(() -> service.verify("c", "0000")).getMessage();

        assertThat(exhausted).isNotEqualTo(expired);
    }

    @Test
    void verifyOfUnknownChallengeAsksForNewLogin() {
        assertThat(failure(() -> service.verify("nope", "1234")).errorCode())
                .isEqualTo(MemberErrorCode.OTP_CHALLENGE_INVALID);
    }

    private MemberErrorCode verifyError(Status status) {
        store.next = status == Status.FAILURE_LIMITED
                ? new OtpStoreResult(status, 0, 0, Duration.ofHours(1)) : OtpStoreResult.of(status);
        return (MemberErrorCode) failure(() -> service.verify("c", "0000")).errorCode();
    }

    private static BusinessException failure(Runnable call) {
        try {
            call.run();
        } catch (BusinessException ex) {
            return ex;
        }
        throw new AssertionError("예외가 나야 한다");
    }

    private static Member member() {
        Member member = Member.registerLocal("tester01", "hash", "tester@example.com", "테스터");
        ReflectionTestUtils.setField(member, "id", 42L);
        return member;
    }

    private static final class FakeStore implements OtpChallengeStore {
        final List<NewOtpChallenge> issued = new ArrayList<>();
        final List<String> resent = new ArrayList<>();
        final List<String> resentDigests = new ArrayList<>();
        final List<String> verified = new ArrayList<>();
        final List<String> compensated = new ArrayList<>();
        OtpChallengeSnapshot snapshot;
        OtpStoreResult next;
        RuntimeException compensationFailure;
        final List<String> confirmed = new ArrayList<>();
        boolean confirmResult = true;

        @Override
        public boolean confirmDelivered(String challengeId, int version) {
            confirmed.add(challengeId + "#" + version);
            return confirmResult;
        }

        @Override
        public OtpStoreResult issueForLogin(NewOtpChallenge challenge, Instant now) {
            issued.add(challenge);
            return next != null ? next : new OtpStoreResult(Status.OK, 1, challenge.memberId(), Duration.ZERO);
        }

        @Override
        public Optional<OtpChallengeSnapshot> find(String challengeId) {
            return snapshot != null && snapshot.challengeId().equals(challengeId) ? Optional.of(snapshot)
                    : Optional.empty();
        }

        @Override
        public OtpStoreResult resend(OtpChallengeSnapshot current, String newOtpHmac, String newCodeDigest,
                                     String keyId, Instant otpExpiresAt, Instant now) {
            resent.add(newOtpHmac);
            resentDigests.add(newCodeDigest);
            return next != null ? next
                    : new OtpStoreResult(Status.OK, current.version() + 1, current.memberId(), Duration.ZERO);
        }

        @Override
        public OtpStoreResult verify(OtpChallengeSnapshot current, String candidateHmac, Instant now) {
            verified.add(candidateHmac);
            return next;
        }

        @Override
        public boolean deliveryFailed(String challengeId, int version, DeliveryStage stage) {
            if (compensationFailure != null) {
                throw compensationFailure;
            }
            compensated.add(challengeId + "#" + version + "#" + stage);
            return true;
        }
    }
}
