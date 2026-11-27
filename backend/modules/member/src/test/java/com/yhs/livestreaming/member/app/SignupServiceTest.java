package com.yhs.livestreaming.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.member.app.password.BreachedPasswordChecker;
import com.yhs.livestreaming.member.app.password.BreachedPasswordChecker.Result;
import com.yhs.livestreaming.member.app.password.PasswordHasher;
import com.yhs.livestreaming.member.domain.Member;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** MB-T017: 회원가입 서비스의 유출 비밀번호 검사 순서와 결과(MB-D010). 저장소·해시·HIBP는 대역이다. */
class SignupServiceTest {

    private static final SignupCommand COMMAND =
            new SignupCommand("tester01", "correct horse battery staple", "tester@example.com", "테스터");

    private static final java.time.Instant NOW = java.time.Instant.parse("2026-11-27T10:00:00Z");
    private static final java.time.Clock CLOCK = java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC);
    private static final java.time.Instant CUTOFF = NOW.minus(java.time.Duration.ofHours(24));

    private final FakeMembers members = new FakeMembers();
    private final CountingHasher hasher = new CountingHasher();

    @Test
    void breachedPasswordIsRejectedBeforeHashingAndSaving() {
        SignupService service = new SignupService(members, hasher, CLOCK, password -> Result.BREACHED);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.signup(COMMAND))
                .satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.PASSWORD_BREACHED))
                .withMessageNotContaining(COMMAND.rawPassword());
        assertThat(hasher.calls).isZero();
        assertThat(members.saved).isEmpty();
    }

    @Test
    void unavailableCheckRejectsSignup() { // fail-closed
        SignupService service = new SignupService(members, hasher, CLOCK, password -> Result.UNAVAILABLE);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.signup(COMMAND))
                .satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.PASSWORD_CHECK_UNAVAILABLE));
        assertThat(hasher.calls).isZero();
        assertThat(members.saved).isEmpty();
    }

    @Test
    void notBreachedPasswordIsHashedAndSaved() {
        SignupService service = new SignupService(members, hasher, CLOCK, password -> Result.NOT_BREACHED);

        service.signup(COMMAND);

        assertThat(hasher.calls).isEqualTo(1);
        assertThat(members.saved).hasSize(1);
    }

    @Test
    void duplicateUsernameIsReportedWithoutCallingHibp() {
        members.usernameExists = true;
        List<String> checked = new ArrayList<>();
        SignupService service = new SignupService(members, hasher, CLOCK, password -> {
            checked.add(password);
            return Result.NOT_BREACHED;
        });

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.signup(COMMAND))
                .satisfies(ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.USERNAME_DUPLICATE));
        assertThat(checked).isEmpty();
    }

    @Test
    void duplicateChecksAndSaveIgnoreExpiredPendingHoldersWithTheSameCutoff() { // MB-T064(MB-D030)
        SignupService service = new SignupService(members, hasher, CLOCK, password -> Result.NOT_BREACHED);

        service.signup(COMMAND);

        assertThat(members.takenCutoffs).containsExactly(CUTOFF, CUTOFF); // username·이메일 사전 검사
        assertThat(members.reclaimCutoffs).containsExactly(CUTOFF); // 실제 회수 저장
        assertThat(members.saved).hasSize(1);
    }

    @Test
    void breachedOrUncheckablePasswordReclaimsNothing() { // MB-T064: HIBP 실패 시 회수 작업 없음
        for (Result result : new Result[] {Result.BREACHED, Result.UNAVAILABLE}) {
            SignupService service = new SignupService(members, hasher, CLOCK, password -> result);
            assertThatExceptionOfType(BusinessException.class).isThrownBy(() -> service.signup(COMMAND));
        }
        assertThat(members.reclaimCutoffs).isEmpty();
        assertThat(members.saved).isEmpty();
    }

    private static final class FakeMembers implements MemberRepository {
        boolean usernameExists;
        final List<Member> saved = new ArrayList<>();
        final List<java.time.Instant> takenCutoffs = new ArrayList<>();
        final List<java.time.Instant> reclaimCutoffs = new ArrayList<>();

        @Override
        public java.util.Optional<Member> findByUsername(String username) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<Member> findById(long id) {
            return java.util.Optional.empty();
        }

        @Override
        public int updatePasswordHash(long id, String expectedHash, String newHash, java.time.Instant now) {
            return 0;
        }

        @Override
        public int activatePending(long id, java.time.Instant createdAfter, java.time.Instant now) {
            return 0;
        }

        @Override
        public java.util.List<Member> findAllByIds(java.util.Collection<Long> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateNicknameOfActive(long id, String nickname, java.time.Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUsernameTaken(String username, java.time.Instant expiredAtOrBefore) {
            takenCutoffs.add(expiredAtOrBefore);
            return usernameExists;
        }

        @Override
        public boolean isLocalEmailTaken(String email, java.time.Instant expiredAtOrBefore) {
            takenCutoffs.add(expiredAtOrBefore);
            return false;
        }

        @Override
        public Member saveReclaimingExpired(Member member, java.time.Instant expiredAtOrBefore) {
            reclaimCutoffs.add(expiredAtOrBefore);
            return save(member);
        }

        @Override
        public Member save(Member member) {
            ReflectionTestUtils.setField(member, "id", (long) saved.size() + 1); // DB IDENTITY 대신
            saved.add(member);
            return member;
        }
    }

    private static final class CountingHasher implements PasswordHasher {
        int calls;

        @Override
        public String hash(String rawPassword) {
            calls++;
            return "pbkdf2-sha256$v1$nfc$i=1$k=test$c2FsdHNhbHRzYWx0c2FsdA$aGFzaA";
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            return false;
        }

        @Override
        public boolean needsRehash(String storedHash) {
            return false;
        }
    }
}
