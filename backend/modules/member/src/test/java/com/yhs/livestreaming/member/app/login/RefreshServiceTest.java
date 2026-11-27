package com.yhs.livestreaming.member.app.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.security.jwt.IssuedAccessToken;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.session.LoginSessionStore.RotateResult;
import com.yhs.livestreaming.member.app.session.RandomTokens;
import com.yhs.livestreaming.member.app.session.RefreshRotation;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MB-T035·MB-T036·MB-T038·MB-T039(서비스): Refresh 순서(조회 → 재사용 탐지 → 회원 재확인 → Access 서명 → Rotation),
 * 상태별 세션 폐기, DB·Redis 장애 시 아무것도 바꾸지 않음, 해시 충돌 1회 재시도(MB-D020, MB-D021).
 */
@ExtendWith(OutputCaptureExtension.class)
class RefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    private static final Instant SESSION_EXPIRES = NOW.plus(Duration.ofDays(3));
    private static final String SID = "S".repeat(43);
    private static final String REFRESH = "R".repeat(43);
    private static final String REFRESH_HASH = RandomTokens.sha256Hex(REFRESH);

    private final LoginFakes.Members members = new LoginFakes.Members();
    private final FakeLoginSessions sessions = new FakeLoginSessions();
    private final List<String> issued = new ArrayList<>();
    private final List<String> newTokens = new ArrayList<>(List.of("N".repeat(43), "M".repeat(43), "K".repeat(43)));
    private final RefreshService service = new RefreshService(members, sessions,
            (memberId, role, sessionId, sessionExpiresAt) -> {
                issued.add(memberId + ":" + role + ":" + sessionId + ":" + sessionExpiresAt);
                return new IssuedAccessToken("jwt-" + issued.size(), NOW.plus(Duration.ofMinutes(15)));
            }, Clock.fixed(NOW, ZoneOffset.UTC), () -> newTokens.remove(0));

    private Member active(long id) {
        Member member = LoginFakes.member(id, "tester0" + id, "h", AccountStatus.ACTIVE, NOW.minus(Duration.ofDays(1)));
        members.add(member);
        return member;
    }

    @Test
    void currentRefreshRotatesKeepingSessionAndExpiry() { // MB-T035
        active(1L);
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);

        CompletedLogin result = service.refresh(REFRESH);

        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.refreshToken()).isEqualTo("N".repeat(43));
        assertThat(result.sessionExpiresAt()).isEqualTo(SESSION_EXPIRES);
        assertThat(issued).containsExactly("1:USER:" + SID + ":" + SESSION_EXPIRES);
        RefreshRotation rotation = sessions.rotations.get(0);
        assertThat(rotation.sessionId()).isEqualTo(SID);
        assertThat(rotation.currentHash()).isEqualTo(REFRESH_HASH);
        assertThat(rotation.newHash()).isEqualTo(RandomTokens.sha256Hex("N".repeat(43)));
        assertThat(rotation.expiresAt()).isEqualTo(SESSION_EXPIRES);
    }

    @Test
    void missingMalformedOrUnknownTokenIsRefreshInvalid() { // MB-T040
        for (String token : new String[] {null, "", "short", "R".repeat(42) + "!", REFRESH}) {
            assertRefreshInvalid(token);
        }
        assertThat(sessions.rotations).isEmpty();
    }

    @Test
    void usedTokenRevokesSessionBeforeLookingAtTheMember(CapturedOutput output) { // MB-T036
        active(1L);
        sessions.put(SID, 1L, "0".repeat(64), SESSION_EXPIRES); // 현재 해시는 다른 값
        sessions.refreshes.put(REFRESH_HASH, new FakeLoginSessions.Refresh(SID, 1L)); // 이전(USED) 해시
        members.findFailure = new IllegalStateException("DB를 보면 안 된다");

        assertRefreshInvalid(REFRESH);

        assertThat(sessions.revoked).containsExactly(SID);
        assertThat(sessions.rotations).isEmpty();
        assertThat(output).contains("Refresh Token 재사용").doesNotContain(SID).doesNotContain(REFRESH)
                .doesNotContain(REFRESH_HASH);
    }

    @Test
    void reuseDetectedInsideRotationIsRefreshInvalid(CapturedOutput output) { // MB-T036·MB-T037
        active(1L);
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);
        sessions.forcedRotateResults.add(RotateResult.REUSED);

        assertRefreshInvalid(REFRESH);

        assertThat(output).contains("Refresh Token 재사용").doesNotContain(SID).doesNotContain(REFRESH_HASH);
    }

    @Test
    void invalidRotationIsRefreshInvalid() { // MB-T037
        active(1L);
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);
        sessions.forcedRotateResults.add(RotateResult.INVALID);

        assertRefreshInvalid(REFRESH);
    }

    @Test
    void pendingMemberIsRejectedAndSessionRevokedBeforeOrAfterPendingExpiry() { // MB-T038
        for (Instant createdAt : new Instant[] {NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofDays(2))}) {
            members.add(LoginFakes.member(1L, "tester01", "h", AccountStatus.PENDING, createdAt));
            sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);

            assertRefreshInvalid(REFRESH);

            assertThat(sessions.sessions).doesNotContainKey(SID);
        }
        assertThat(sessions.rotations).isEmpty();
        assertThat(issued).isEmpty();
    }

    @Test
    void missingMemberIsRejectedAndSessionRevoked() { // MB-T038
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);

        assertRefreshInvalid(REFRESH);

        assertThat(sessions.revoked).containsExactly(SID);
        assertThat(issued).isEmpty();
    }

    @Test
    void disabledMemberIsForbiddenAndSessionRevoked() { // MB-T038
        Member member = active(1L);
        ReflectionTestUtils.setField(member, "accountStatus", AccountStatus.DISABLED);
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);

        assertThatThrownBy(() -> service.refresh(REFRESH))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.ACCOUNT_DISABLED));

        assertThat(sessions.revoked).containsExactly(SID);
        assertThat(sessions.rotations).isEmpty();
    }

    @Test
    void databaseFailureChangesNothingAndIsNotTreatedAsMissingMember() { // MB-T039
        active(1L);
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);
        members.findFailure = new DataAccessResourceFailureException("db down");

        assertThatThrownBy(() -> service.refresh(REFRESH)).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(sessions.revoked).isEmpty();
        assertThat(sessions.rotations).isEmpty();
        assertThat(issued).isEmpty();
        assertThat(sessions.sessions.get(SID).refreshHash()).isEqualTo(REFRESH_HASH);
    }

    @Test
    void storeFailureAtLookupOrRotationPropagatesAsUnavailable() { // MB-T039
        active(1L);
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);
        sessions.findFailure = new LoginSessionStoreUnavailableException("down", null);
        assertThatThrownBy(() -> service.refresh(REFRESH)).isInstanceOf(LoginSessionStoreUnavailableException.class);

        sessions.findFailure = null;
        sessions.rotateFailure = new LoginSessionStoreUnavailableException("down", null);
        assertThatThrownBy(() -> service.refresh(REFRESH)).isInstanceOf(LoginSessionStoreUnavailableException.class);
        assertThat(sessions.sessions.get(SID).refreshHash()).isEqualTo(REFRESH_HASH);
    }

    @Test
    void hashCollisionRetriesOnceWithANewToken() { // MB-T035
        active(1L);
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);
        sessions.forcedRotateResults.add(RotateResult.COLLISION);

        CompletedLogin result = service.refresh(REFRESH);

        assertThat(result.refreshToken()).isEqualTo("M".repeat(43));
        assertThat(sessions.rotations).hasSize(2);
        assertThat(sessions.rotations.get(1).newHash()).isNotEqualTo(sessions.rotations.get(0).newHash());
    }

    @Test
    void secondHashCollisionIsAnInternalErrorWithoutTokens() { // MB-T035
        active(1L);
        sessions.put(SID, 1L, REFRESH_HASH, SESSION_EXPIRES);
        sessions.forcedRotateResults.add(RotateResult.COLLISION);
        sessions.forcedRotateResults.add(RotateResult.COLLISION);

        assertThatThrownBy(() -> service.refresh(REFRESH))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(BusinessException.class);
        assertThat(sessions.sessions.get(SID).refreshHash()).isEqualTo(REFRESH_HASH);
        assertThat(sessions.revoked).isEmpty();
    }

    private void assertRefreshInvalid(String token) {
        assertThatThrownBy(() -> service.refresh(token))
                .as("token=%s", token == null ? "null" : token.length())
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.REFRESH_INVALID));
    }
}
