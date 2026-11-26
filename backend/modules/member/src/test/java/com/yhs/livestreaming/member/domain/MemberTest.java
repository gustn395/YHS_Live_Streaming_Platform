package com.yhs.livestreaming.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** MB-T001: 일반 가입 회원은 LOCAL·PENDING·이메일 미확인·USER로 만들어진다(MB-D003, 명세 M-01). */
class MemberTest {

    @Test
    void registerLocalStartsAsPendingUnverifiedUser() {
        Member member = Member.registerLocal("tester01", "pbkdf2-sha256$hash", "tester@example.com", "테스터");

        assertThat(member.getAccountType()).isEqualTo(AccountType.LOCAL);
        assertThat(member.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(member.isEmailVerified()).isFalse();
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getUsername()).isEqualTo("tester01");
        assertThat(member.getPasswordHash()).isEqualTo("pbkdf2-sha256$hash");
        assertThat(member.getEmail()).isEqualTo("tester@example.com");
        assertThat(member.getNickname()).isEqualTo("테스터");
    }

    @Test
    void pendingExpiresExactlyTwentyFourHoursAfterSignup() { // MB-D003, MB-T030
        Member member = Member.registerLocal("tester01", "hash", "tester@example.com", "테스터");
        java.time.Instant createdAt = java.time.Instant.parse("2026-11-26T00:00:00Z");
        org.springframework.test.util.ReflectionTestUtils.setField(member, "createdAt", createdAt);

        assertThat(member.isPendingExpired(createdAt.plus(java.time.Duration.ofHours(24)).minusMillis(1))).isFalse();
        assertThat(member.isPendingExpired(createdAt.plus(java.time.Duration.ofHours(24)))).isTrue();

        org.springframework.test.util.ReflectionTestUtils.setField(member, "accountStatus", AccountStatus.ACTIVE);
        assertThat(member.isPendingExpired(createdAt.plus(java.time.Duration.ofDays(30)))).isFalse();
    }

    @Test
    void localMemberRequiresUsernameAndPasswordHash() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Member.registerLocal(" ", "hash", "tester@example.com", "테스터"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Member.registerLocal("tester01", null, "tester@example.com", "테스터"));
    }
}
