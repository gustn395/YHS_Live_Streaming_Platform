package com.yhs.livestreaming.member.app.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.member.app.otp.LoginChallenge;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MB-T045·MB-T047·MB-T048(서비스): Google 1차 인증 → GOOGLE 회원 확인·자동 가입 → 기존 OTP 시작(MB-D023, MB-D025).
 * 로그인 완료·쿠키는 여기서 하지 않는다.
 */
class GoogleLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
    private static final String SUB = "sub-123";

    private final FakeGoogleAccounts accounts = new FakeGoogleAccounts();
    private final List<String> otpStarted = new ArrayList<>();
    private final GoogleLoginService service = new GoogleLoginService(accounts, (member, deliveryEmail) -> {
        otpStarted.add(member.getId() + ":" + deliveryEmail);
        return new LoginChallenge("challenge", NOW.plusSeconds(300), NOW.plusSeconds(600));
    }, Clock.fixed(NOW, ZoneOffset.UTC), () -> "123456");

    @Test
    void firstLoginCreatesPendingGoogleMemberAndStartsOtpToTheGoogleEmail() {
        LoginChallenge challenge = service.login(new GoogleIdentity(SUB, " New@Example.COM ", true, "  구글 사용자 "));

        Member created = accounts.bySub.get(SUB);
        assertThat(created.getAccountType()).isEqualTo(AccountType.GOOGLE);
        assertThat(created.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(created.isEmailVerified()).isFalse();
        assertThat(created.getEmail()).isEqualTo("new@example.com");
        assertThat(created.getNickname()).isEqualTo("구글 사용자");
        assertThat(otpStarted).containsExactly(created.getId() + ":new@example.com");
        assertThat(challenge.challengeId()).isEqualTo("challenge");
    }

    @Test
    void existingMemberIsFoundBySubAndGetsOtpAtTheCurrentGoogleEmailWithoutChangingStoredEmail() {
        Member existing = accounts.add(SUB, Member.registerGoogle("old@example.com", "사용자"), 7L,
                NOW.minus(Duration.ofDays(30)));
        ReflectionTestUtils.setField(existing, "accountStatus", AccountStatus.ACTIVE);

        service.login(new GoogleIdentity(SUB, "changed@example.com", true, "다른 이름"));

        assertThat(otpStarted).containsExactly("7:changed@example.com");
        assertThat(existing.getEmail()).isEqualTo("old@example.com"); // OTP 성공 전에는 바꾸지 않는다
        assertThat(existing.getNickname()).isEqualTo("사용자"); // Google 이름 변경은 반영하지 않는다
        assertThat(accounts.calls).doesNotContain("create");
    }

    @Test
    void unverifiedOrMissingEmailIsRejectedWithoutCreatingAMember() {
        for (GoogleIdentity identity : List.of(new GoogleIdentity(SUB, "a@example.com", false, "n"),
                new GoogleIdentity(SUB, null, true, "n"), new GoogleIdentity(SUB, "  ", true, "n"))) {
            assertThatThrownBy(() -> service.login(identity))
                    .isInstanceOfSatisfying(OAuthLoginException.class,
                            ex -> assertThat(ex.failure()).isEqualTo(OAuthFailure.EMAIL_UNVERIFIED));
        }
        assertThat(accounts.bySub).isEmpty();
        assertThat(otpStarted).isEmpty();
    }

    @Test
    void disabledMemberIsRejectedWithoutOtp() {
        Member existing = accounts.add(SUB, Member.registerGoogle("g@example.com", "사용자"), 7L, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(existing, "accountStatus", AccountStatus.DISABLED);

        assertThatThrownBy(() -> service.login(new GoogleIdentity(SUB, "g@example.com", true, "n")))
                .isInstanceOfSatisfying(OAuthLoginException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(OAuthFailure.ACCOUNT_DISABLED));
        assertThat(otpStarted).isEmpty();
    }

    @Test
    void validPendingMemberGoesToOtpAsIs() {
        accounts.add(SUB, Member.registerGoogle("g@example.com", "사용자"), 7L, NOW.minus(Duration.ofHours(23)));

        service.login(new GoogleIdentity(SUB, "g@example.com", true, "n"));

        assertThat(otpStarted).containsExactly("7:g@example.com");
        assertThat(accounts.calls).doesNotContain("replace", "create");
    }

    @Test
    void expiredPendingMemberIsReplacedByANewPendingMember() {
        accounts.add(SUB, Member.registerGoogle("g@example.com", "옛이름"), 7L, NOW.minus(Duration.ofHours(24)));

        service.login(new GoogleIdentity(SUB, "g@example.com", true, "새 이름"));

        Member replaced = accounts.bySub.get(SUB);
        assertThat(replaced.getId()).isNotEqualTo(7L);
        assertThat(replaced.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(replaced.getNickname()).isEqualTo("새 이름");
        assertThat(otpStarted).containsExactly(replaced.getId() + ":g@example.com");
    }

    @Test
    void concurrentFirstLoginConflictReadsTheMemberCreatedByTheOtherRequest() {
        accounts.beforeCreate.add(() -> accounts.add(SUB, Member.registerGoogle("g@example.com", "먼저"), 9L, NOW));

        service.login(new GoogleIdentity(SUB, "g@example.com", true, "나중"));

        assertThat(otpStarted).containsExactly("9:g@example.com");
        assertThat(accounts.calls).containsExactly("find", "create", "find");
    }

    @Test
    void databaseFailurePropagatesWithoutOtp() {
        accounts.findFailure = new DataAccessResourceFailureException("db down");

        assertThatThrownBy(() -> service.login(new GoogleIdentity(SUB, "g@example.com", true, "n")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(otpStarted).isEmpty();
    }
}
