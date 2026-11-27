package com.yhs.livestreaming.member.app.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.security.jwt.IssuedAccessToken;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.session.LoginSessionStore.CreateResult;
import com.yhs.livestreaming.member.app.oauth.FakeGoogleAccounts;
import com.yhs.livestreaming.member.app.otp.VerifiedOtp;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import com.yhs.livestreaming.member.domain.Member;
import org.springframework.test.util.ReflectionTestUtils;
import com.yhs.livestreaming.member.domain.AccountStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MB-T022·MB-T027·MB-T030(서비스): OTP 성공 뒤 상태 재확인 → 조건부 활성화 → 세션·Refresh 저장 → Access 발급(MB-D011, MB-D014).
 */
class LoginCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    private final LoginFakes.Members members = new LoginFakes.Members();
    private final FakeGoogleAccounts googleAccounts = new FakeGoogleAccounts();
    private final List<NewLoginSession> stored = new ArrayList<>();
    private final Deque<CreateResult> createResults = new ArrayDeque<>();
    private RuntimeException storeFailure;
    private final List<String> issuedFor = new ArrayList<>();
    private final LoginCompletionService service = new LoginCompletionService(members, new FakeLoginSessions() {
        @Override
        public CreateResult create(NewLoginSession session) {
            if (storeFailure != null) {
                throw storeFailure;
            }
            stored.add(session);
            return createResults.isEmpty() ? CreateResult.CREATED : createResults.pop();
        }
    }, (memberId, role, sessionId, sessionExpiresAt) -> {
        issuedFor.add(memberId + ":" + role + ":" + sessionId);
        return new IssuedAccessToken("jwt-" + sessionId, NOW.plus(Duration.ofMinutes(15)));
    }, Clock.fixed(NOW, ZoneOffset.UTC), googleAccounts);

    @Test
    void activeMemberGetsSessionWithSevenDayAbsoluteExpiryAndHashedRefresh() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.ACTIVE, NOW.minusSeconds(10)));

        CompletedLogin login = service.complete(new VerifiedOtp(1L, "local@example.com"));

        NewLoginSession session = stored.get(0);
        assertThat(session.memberId()).isEqualTo(1L);
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(session.sessionId()).matches("[A-Za-z0-9_-]{43}");
        assertThat(login.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(session.refreshTokenHash()).matches("[0-9a-f]{64}").isNotEqualTo(login.refreshToken())
                .isEqualTo(sha256Hex(login.refreshToken())); // 저장값은 반환한 Refresh 원문의 SHA-256
        assertThat(login.accessToken().value()).isEqualTo("jwt-" + session.sessionId());
        assertThat(login.sessionExpiresAt()).isEqualTo(session.expiresAt());
        assertThat(issuedFor).containsExactly("1:USER:" + session.sessionId());
        assertThat(members.activateCalls).isZero();
    }

    @Test
    void validPendingMemberIsActivatedBeforeTokens() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minus(Duration.ofHours(2))));

        service.complete(new VerifiedOtp(1L, "local@example.com"));

        assertThat(members.activateCalls).isEqualTo(1);
        assertThat(members.byId.get(1L).getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(members.byId.get(1L).isEmailVerified()).isTrue();
        assertThat(stored).hasSize(1);
    }

    @Test
    void memberDisabledWhileWaitingForOtpGetsNoTokens() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.DISABLED, NOW));

        assertThat(failure(() -> service.complete(new VerifiedOtp(1L, "local@example.com"))).errorCode()).isEqualTo(MemberErrorCode.ACCOUNT_DISABLED);
        assertThat(stored).isEmpty();
    }

    @Test
    void pendingThatExpiredWhileWaitingGetsGenericFailure() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minus(Duration.ofHours(24))));

        assertThat(failure(() -> service.complete(new VerifiedOtp(1L, "local@example.com"))).errorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED);
        assertThat(stored).isEmpty();
    }

    @Test
    void zeroRowActivationRechecksStateAndRefusesTokens() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minus(Duration.ofHours(2))));
        members.activateRows = 0; // 조건부 UPDATE가 0행: 재조회해도 ACTIVE가 아니면 토큰을 주지 않는다

        assertThat(failure(() -> service.complete(new VerifiedOtp(1L, "local@example.com"))).errorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED);
        assertThat(stored).isEmpty();
    }

    @Test
    void zeroRowActivationButAlreadyActiveProceeds() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minus(Duration.ofHours(2))));
        members.activateRows = 0;
        // 다른 요청이 먼저 활성화한 상황: 재조회 결과가 ACTIVE
        members.onActivate = () -> org.springframework.test.util.ReflectionTestUtils.setField(
                members.byId.get(1L), "accountStatus", AccountStatus.ACTIVE);

        service.complete(new VerifiedOtp(1L, "local@example.com"));

        assertThat(stored).hasSize(1);
    }

    @Test
    void missingMemberGetsGenericFailure() {
        assertThat(failure(() -> service.complete(new VerifiedOtp(99L, "local@example.com"))).errorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED);
    }

    @Test
    void sessionKeyCollisionRetriesOnceWithNewIdentifiers() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.ACTIVE, NOW));
        createResults.add(CreateResult.COLLISION);

        service.complete(new VerifiedOtp(1L, "local@example.com"));

        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).sessionId()).isNotEqualTo(stored.get(1).sessionId());
    }

    @Test
    void repeatedCollisionOrStoreFailureMeansNoTokens() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.ACTIVE, NOW));
        createResults.add(CreateResult.COLLISION);
        createResults.add(CreateResult.COLLISION);

        assertThatThrownBy(() -> service.complete(new VerifiedOtp(1L, "local@example.com"))).isInstanceOf(LoginSessionStoreUnavailableException.class);

        storeFailure = new LoginSessionStoreUnavailableException("down", null);
        assertThatThrownBy(() -> service.complete(new VerifiedOtp(1L, "local@example.com"))).isInstanceOf(LoginSessionStoreUnavailableException.class);
    }

    // ----- GOOGLE (MB-D025): OTP 최종 성공 때만 이메일을 Challenge의 수신 주소로 갱신 -----

    private Member google(long id, AccountStatus status, String email, Instant createdAt) {
        Member member = Member.registerGoogle(email, "구글");
        googleAccounts.add("sub-" + id, member, id, createdAt);
        ReflectionTestUtils.setField(member, "accountStatus", status);
        members.add(member);
        return member;
    }

    @Test
    void googlePendingIsActivatedWithTheDeliveryEmailAndGetsASession() { // MB-T046
        Member member = google(5L, AccountStatus.PENDING, "old@example.com", NOW.minus(Duration.ofHours(1)));

        CompletedLogin login = service.complete(new VerifiedOtp(5L, "new@example.com"));

        assertThat(googleAccounts.calls).containsExactly("activate:new@example.com");
        assertThat(member.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(member.isEmailVerified()).isTrue();
        assertThat(googleAccounts.linkEmail.get(5L)).isEqualTo("new@example.com");
        assertThat(members.activateCalls).isZero(); // LOCAL 활성화 경로를 쓰지 않는다
        assertThat(login.memberId()).isEqualTo(5L);
        assertThat(stored).hasSize(1);
    }

    @Test
    void googleActiveWithAChangedEmailIsUpdatedAndSameEmailIsLeftAlone() { // MB-T046
        Member member = google(6L, AccountStatus.ACTIVE, "old@example.com", NOW.minus(Duration.ofDays(3)));

        service.complete(new VerifiedOtp(6L, "old@example.com"));
        assertThat(googleAccounts.calls).isEmpty();

        service.complete(new VerifiedOtp(6L, "new@example.com"));
        assertThat(googleAccounts.calls).containsExactly("email:new@example.com");
        assertThat(member.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void googleEmailUpdateRaceWithDisablingRefusesTheLogin() { // MB-T046: 갱신 0행이면 다시 확인
        Member member = google(6L, AccountStatus.ACTIVE, "old@example.com", NOW.minus(Duration.ofDays(3)));
        LoginCompletionService racing = new LoginCompletionService(members, new FakeLoginSessions(),
                (memberId, role, sessionId, sessionExpiresAt) -> {
                    throw new AssertionError("토큰을 발급하면 안 된다");
                }, Clock.fixed(NOW, ZoneOffset.UTC), new FakeGoogleAccounts() {
                    @Override
                    public int updateActiveEmail(long memberId, String email, Instant now) {
                        ReflectionTestUtils.setField(member, "accountStatus", AccountStatus.DISABLED);
                        return 0;
                    }
                });

        assertThat(failure(() -> racing.complete(new VerifiedOtp(6L, "new@example.com"))).errorCode())
                .isEqualTo(MemberErrorCode.ACCOUNT_DISABLED);
        assertThat(member.getEmail()).isEqualTo("old@example.com");
    }

    @Test
    void expiredGooglePendingIsRefusedAndKeepsItsEmail() { // MB-T046
        Member member = google(7L, AccountStatus.PENDING, "old@example.com", NOW.minus(Duration.ofHours(25)));

        assertThat(failure(() -> service.complete(new VerifiedOtp(7L, "new@example.com"))).errorCode())
                .isEqualTo(MemberErrorCode.LOGIN_FAILED);
        assertThat(member.getEmail()).isEqualTo("old@example.com");
        assertThat(googleAccounts.calls).isEmpty();
        assertThat(stored).isEmpty();
    }

    @Test
    void localMemberIgnoresTheDeliveryEmailAndUsesTheLocalActivation() { // MB-T053
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minusSeconds(10)));

        service.complete(new VerifiedOtp(1L, "tester01@example.com"));

        assertThat(members.activateCalls).isEqualTo(1);
        assertThat(googleAccounts.calls).isEmpty();
    }

    private static String sha256Hex(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
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
