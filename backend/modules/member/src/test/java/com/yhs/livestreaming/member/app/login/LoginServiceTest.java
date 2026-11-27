package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.member.app.otp.LoginChallenge;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStarter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.TooManyRequestsException;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.password.DummyPasswordHash;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MB-T019·MB-T020·MB-T031(서비스): 1차 인증(MB-D011, MB-D017, MB-D006 세부 해석). 저장소·해시·제한·OTP 발급은 대역이다.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final String PASSWORD = "correct horse battery staple";

    private final LoginFakes.Members members = new LoginFakes.Members();
    private final LoginFakes.Hasher hasher = new LoginFakes.Hasher();
    private final LoginFakes.Attempts attempts = new LoginFakes.Attempts();
    private final List<Long> otpStartedFor = new ArrayList<>();
    private final List<String> otpEmails = new ArrayList<>();
    private final DummyPasswordHash dummy = new DummyPasswordHash(hasher, "pw:dummy");
    private final LoginService service = new LoginService(members, hasher, dummy, attempts,
            (member, deliveryEmail) -> {
                otpStartedFor.add(member.getId());
                otpEmails.add(deliveryEmail);
                return new LoginChallenge("challenge", NOW.plusSeconds(300), NOW.plusSeconds(600));
            },
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void activeMemberWithCorrectPasswordStartsOtpAndResetsFailures() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.ACTIVE, NOW.minusSeconds(60)));

        LoginChallenge challenge = service.login("tester01", PASSWORD);

        assertThat(challenge.challengeId()).isEqualTo("challenge");
        assertThat(otpStartedFor).containsExactly(1L);
        assertThat(attempts.successes).containsExactly("tester01");
        assertThat(otpEmails).containsExactly("tester01@example.com"); // LOCAL은 회원 이메일로 보낸다(MB-T053)
    }

    @Test
    void pendingMemberWithinTwentyFourHoursAlsoStartsOtp() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.PENDING,
                NOW.minus(Duration.ofHours(23))));

        service.login("tester01", PASSWORD);

        assertThat(otpStartedFor).containsExactly(1L);
    }

    @Test
    void wrongPasswordUnknownUsernameGoogleAndExpiredPendingAllFailTheSameWay() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.ACTIVE, NOW));
        members.add(LoginFakes.member(2L, "expired1", "pw:" + PASSWORD, AccountStatus.PENDING,
                NOW.minus(Duration.ofHours(24))));
        Member google = LoginFakes.member(3L, "google01", "pw:" + PASSWORD, AccountStatus.ACTIVE, NOW);
        ReflectionTestUtils.setField(google, "accountType", com.yhs.livestreaming.member.domain.AccountType.GOOGLE);
        members.add(google);

        List<BusinessException> failures = List.of(
                failure(() -> service.login("tester01", "wrong password here")),
                failure(() -> service.login("nobody01", PASSWORD)),
                failure(() -> service.login("google01", PASSWORD)),
                failure(() -> service.login("expired1", PASSWORD)));

        assertThat(failures).allSatisfy(ex -> {
            assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED);
            assertThat(ex.getMessage()).isEqualTo(failures.get(0).getMessage());
        });
        assertThat(otpStartedFor).isEmpty();
        assertThat(attempts.successes).isEmpty();
        assertThat(attempts.failures).containsKeys("tester01", "nobody01", "google01", "expired1");
    }

    @Test
    void unknownAndGoogleUsernamesUseTheDummyHash() {
        Member google = LoginFakes.member(3L, "google01", "x", AccountStatus.ACTIVE, NOW);
        ReflectionTestUtils.setField(google, "accountType", com.yhs.livestreaming.member.domain.AccountType.GOOGLE);
        ReflectionTestUtils.setField(google, "passwordHash", null);
        members.add(google);

        failure(() -> service.login("nobody01", PASSWORD));
        failure(() -> service.login("google01", PASSWORD));

        assertThat(hasher.matched).containsExactly("pw:dummy", "pw:dummy");
    }

    @Test
    void expiredPendingCountsAsFailureAndDoesNotResetCounter() {
        members.add(LoginFakes.member(2L, "expired1", "pw:" + PASSWORD, AccountStatus.PENDING,
                NOW.minus(Duration.ofHours(24))));
        attempts.failures.put("expired1", 3);

        failure(() -> service.login("expired1", PASSWORD));

        assertThat(attempts.failures).containsEntry("expired1", 4);
        assertThat(attempts.successes).isEmpty();
    }

    @Test
    void disabledMemberWithCorrectPasswordIsForbiddenWithoutOtpOrReset() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.DISABLED, NOW));
        attempts.failures.put("tester01", 2);

        assertThat(failure(() -> service.login("tester01", PASSWORD)).errorCode())
                .isEqualTo(MemberErrorCode.ACCOUNT_DISABLED);
        assertThat(otpStartedFor).isEmpty();
        assertThat(attempts.failures).containsEntry("tester01", 2);
        assertThat(hasher.needsRehash).isFalse();
    }

    @Test
    void disabledMemberWithWrongPasswordGetsTheGenericFailure() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.DISABLED, NOW));

        assertThat(failure(() -> service.login("tester01", "wrong password here")).errorCode())
                .isEqualTo(MemberErrorCode.LOGIN_FAILED);
    }

    @Test
    void tenthFailureIsTooManyRequests() {
        attempts.failures.put("nobody01", 9);

        assertThatThrownBy(() -> service.login("nobody01", PASSWORD))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.LOGIN_TEMPORARILY_BLOCKED);
                    assertThat(ex.retryAfter()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void blockedUsernameIsRejectedWithoutCheckingThePassword() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.ACTIVE, NOW));
        attempts.blocked = Duration.ofMinutes(7);

        assertThatThrownBy(() -> service.login("tester01", PASSWORD))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofMinutes(7)));
        assertThat(hasher.matched).isEmpty();
    }

    @Test
    void blockCreatedByAnotherRequestDuringPasswordCheckWins() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.ACTIVE, NOW));
        attempts.blockedOnSuccess = Duration.ofMinutes(15);

        assertThatThrownBy(() -> service.login("tester01", PASSWORD))
                .isInstanceOf(TooManyRequestsException.class);
        assertThat(otpStartedFor).isEmpty();
    }

    @Test
    void rehashHappensAfterSuccessfulCheckWhenParametersChanged() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.ACTIVE, NOW));
        hasher.needsRehash = true;

        service.login("tester01", PASSWORD);

        assertThat(members.rehashed).containsExactly("pw2:" + PASSWORD);
    }

    @Test
    void rehashFailureKeepsOldHashAndLoginContinuesWithoutLeakingSecrets(CapturedOutput output) {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.ACTIVE, NOW));
        hasher.needsRehash = true;
        members.rehashFailure = new IllegalStateException("update failed for pw2:" + PASSWORD);

        service.login("tester01", PASSWORD);

        assertThat(otpStartedFor).containsExactly(1L);
        assertThat(output).doesNotContain(PASSWORD).contains("재해시");
    }

    @Test
    void noRehashForFailedOrDisabledLogins() {
        members.add(LoginFakes.member(1L, "tester01", "pw:" + PASSWORD, AccountStatus.DISABLED, NOW));
        hasher.needsRehash = true;

        failure(() -> service.login("tester01", PASSWORD));
        failure(() -> service.login("tester01", "wrong password here"));

        assertThat(members.rehashed).isEmpty();
    }

    private static BusinessException failure(Runnable call) {
        try {
            call.run();
        } catch (BusinessException ex) {
            return ex;
        }
        throw new AssertionError("예외가 나야 한다");
    }
}
