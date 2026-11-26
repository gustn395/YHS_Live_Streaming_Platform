package com.yhs.livestreaming.member.app.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.security.jwt.IssuedAccessToken;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.session.LoginSessionStore.CreateResult;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
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
    private final List<NewLoginSession> stored = new ArrayList<>();
    private final Deque<CreateResult> createResults = new ArrayDeque<>();
    private RuntimeException storeFailure;
    private final List<String> issuedFor = new ArrayList<>();
    private final LoginCompletionService service = new LoginCompletionService(members, session -> {
        if (storeFailure != null) {
            throw storeFailure;
        }
        stored.add(session);
        return createResults.isEmpty() ? CreateResult.CREATED : createResults.pop();
    }, (memberId, role, sessionId, sessionExpiresAt) -> {
        issuedFor.add(memberId + ":" + role + ":" + sessionId);
        return new IssuedAccessToken("jwt-" + sessionId, NOW.plus(Duration.ofMinutes(15)));
    }, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void activeMemberGetsSessionWithSevenDayAbsoluteExpiryAndHashedRefresh() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.ACTIVE, NOW.minusSeconds(10)));

        CompletedLogin login = service.complete(1L);

        NewLoginSession session = stored.get(0);
        assertThat(session.memberId()).isEqualTo(1L);
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(session.sessionId()).matches("[A-Za-z0-9_-]{43}");
        assertThat(login.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(session.refreshTokenHash()).matches("[0-9a-f]{64}").isNotEqualTo(login.refreshToken());
        assertThat(login.accessToken().value()).isEqualTo("jwt-" + session.sessionId());
        assertThat(login.sessionExpiresAt()).isEqualTo(session.expiresAt());
        assertThat(issuedFor).containsExactly("1:USER:" + session.sessionId());
        assertThat(members.activateCalls).isZero();
    }

    @Test
    void validPendingMemberIsActivatedBeforeTokens() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minus(Duration.ofHours(2))));

        service.complete(1L);

        assertThat(members.activateCalls).isEqualTo(1);
        assertThat(members.byId.get(1L).getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(members.byId.get(1L).isEmailVerified()).isTrue();
        assertThat(stored).hasSize(1);
    }

    @Test
    void memberDisabledWhileWaitingForOtpGetsNoTokens() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.DISABLED, NOW));

        assertThat(failure(() -> service.complete(1L)).errorCode()).isEqualTo(MemberErrorCode.ACCOUNT_DISABLED);
        assertThat(stored).isEmpty();
    }

    @Test
    void pendingThatExpiredWhileWaitingGetsGenericFailure() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minus(Duration.ofHours(24))));

        assertThat(failure(() -> service.complete(1L)).errorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED);
        assertThat(stored).isEmpty();
    }

    @Test
    void zeroRowActivationRechecksStateAndRefusesTokens() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minus(Duration.ofHours(2))));
        members.activateRows = 0; // 조건부 UPDATE가 0행: 재조회해도 ACTIVE가 아니면 토큰을 주지 않는다

        assertThat(failure(() -> service.complete(1L)).errorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED);
        assertThat(stored).isEmpty();
    }

    @Test
    void zeroRowActivationButAlreadyActiveProceeds() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, NOW.minus(Duration.ofHours(2))));
        members.activateRows = 0;
        // 다른 요청이 먼저 활성화한 상황: 재조회 결과가 ACTIVE
        members.onActivate = () -> org.springframework.test.util.ReflectionTestUtils.setField(
                members.byId.get(1L), "accountStatus", AccountStatus.ACTIVE);

        service.complete(1L);

        assertThat(stored).hasSize(1);
    }

    @Test
    void missingMemberGetsGenericFailure() {
        assertThat(failure(() -> service.complete(99L)).errorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED);
    }

    @Test
    void sessionKeyCollisionRetriesOnceWithNewIdentifiers() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.ACTIVE, NOW));
        createResults.add(CreateResult.COLLISION);

        service.complete(1L);

        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).sessionId()).isNotEqualTo(stored.get(1).sessionId());
    }

    @Test
    void repeatedCollisionOrStoreFailureMeansNoTokens() {
        members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.ACTIVE, NOW));
        createResults.add(CreateResult.COLLISION);
        createResults.add(CreateResult.COLLISION);

        assertThatThrownBy(() -> service.complete(1L)).isInstanceOf(LoginSessionStoreUnavailableException.class);

        storeFailure = new LoginSessionStoreUnavailableException("down", null);
        assertThatThrownBy(() -> service.complete(1L)).isInstanceOf(LoginSessionStoreUnavailableException.class);
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
