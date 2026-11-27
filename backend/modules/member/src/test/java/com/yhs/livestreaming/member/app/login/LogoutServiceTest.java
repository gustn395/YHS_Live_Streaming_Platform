package com.yhs.livestreaming.member.app.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.session.RandomTokens;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * MB-T041(서비스): 로그아웃 대상 세션 찾기(Access sid·Refresh 해시, USED 포함)·소유자 확인·멱등·일부 실패 뒤 재시도(MB-D022).
 */
class LogoutServiceTest {

    private static final Instant EXPIRES = Instant.parse("2026-12-01T00:00:00Z");
    private static final String SID_A = "A".repeat(43);
    private static final String SID_B = "B".repeat(43);
    private static final String REFRESH = "R".repeat(43);
    private static final String REFRESH_HASH = RandomTokens.sha256Hex(REFRESH);

    private final FakeLoginSessions sessions = new FakeLoginSessions();
    private final LogoutService service = new LogoutService(sessions);

    @Test
    void nothingToRevokeIsStillASuccess() {
        service.logout(null, null);
        service.logout(null, "malformed");
        service.logout(null, REFRESH); // 모르는 토큰

        assertThat(sessions.revoked).isEmpty();
    }

    @Test
    void accessOnlyRevokesItsSession() {
        sessions.put(SID_A, 1L, "0".repeat(64), EXPIRES);

        service.logout(new AuthenticatedMember(1L, "USER", SID_A), null);

        assertThat(sessions.revoked).containsExactly(SID_A);
    }

    @Test
    void refreshOnlyRevokesItsSessionEvenWhenAccessExpired() {
        sessions.put(SID_A, 1L, REFRESH_HASH, EXPIRES);

        service.logout(null, REFRESH);

        assertThat(sessions.revoked).containsExactly(SID_A);
    }

    @Test
    void usedRefreshStillRevokesItsSession() {
        sessions.put(SID_A, 1L, "0".repeat(64), EXPIRES);
        sessions.refreshes.put(REFRESH_HASH, new FakeLoginSessions.Refresh(SID_A, 1L));

        service.logout(null, REFRESH);

        assertThat(sessions.revoked).containsExactly(SID_A);
    }

    @Test
    void sameSessionFromBothCookiesIsRevokedOnce() {
        sessions.put(SID_A, 1L, REFRESH_HASH, EXPIRES);

        service.logout(new AuthenticatedMember(1L, "USER", SID_A), REFRESH);

        assertThat(sessions.revoked).containsExactly(SID_A);
    }

    @Test
    void differentSessionsFromAccessAndRefreshAreBothRevoked() {
        sessions.put(SID_A, 1L, "0".repeat(64), EXPIRES);
        sessions.put(SID_B, 2L, REFRESH_HASH, EXPIRES);

        service.logout(new AuthenticatedMember(1L, "USER", SID_A), REFRESH);

        assertThat(sessions.revoked).containsExactlyInAnyOrder(SID_A, SID_B);
    }

    @Test
    void partialFailureIsUnavailableAndRetryFinishesTheRest() {
        sessions.put(SID_A, 1L, "0".repeat(64), EXPIRES);
        sessions.put(SID_B, 2L, REFRESH_HASH, EXPIRES);
        sessions.failRevokeFor.add(SID_B);
        AuthenticatedMember access = new AuthenticatedMember(1L, "USER", SID_A);

        assertThatThrownBy(() -> service.logout(access, REFRESH))
                .isInstanceOf(LoginSessionStoreUnavailableException.class);
        assertThat(sessions.revoked).containsExactly(SID_A);

        sessions.failRevokeFor.clear();
        service.logout(access, REFRESH);

        assertThat(sessions.revoked).containsExactly(SID_A, SID_B);
        assertThat(sessions.sessions).isEmpty();
    }

    @Test
    void refreshLookupFailureIsUnavailable() {
        sessions.findFailure = new LoginSessionStoreUnavailableException("down", null);

        assertThatThrownBy(() -> service.logout(null, REFRESH))
                .isInstanceOf(LoginSessionStoreUnavailableException.class);
    }

    @Test
    void sessionOwnedByAnotherMemberIsNotRevoked() {
        sessions.put(SID_A, 2L, "0".repeat(64), EXPIRES);

        service.logout(new AuthenticatedMember(1L, "USER", SID_A), null);

        assertThat(sessions.sessions).containsKey(SID_A);
    }
}
