package com.yhs.livestreaming.member.app.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.CommonErrorCode;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.MemberRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** MB-T055·MB-T056·MB-T057(서비스): 내 정보 조회·닉네임 수정과 매 요청 상태 재확인·세션 폐기(MB-D028). */
class MemberProfileServiceTest {

    private static final String SID = "S".repeat(43);
    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

    private final ProfileFakes.Members members = new ProfileFakes.Members();
    private final ProfileFakes.Sessions sessions = new ProfileFakes.Sessions();
    private final MemberProfileService service =
            new MemberProfileService(members, sessions, Clock.fixed(NOW, ZoneOffset.UTC));

    private static AuthenticatedMember me(long id) {
        return new AuthenticatedMember(id, "USER", SID);
    }

    @Test
    void activeLocalMemberGetsAllProfileFields() { // MB-T055
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));

        MemberProfile profile = service.getMyProfile(me(1L));

        assertThat(profile).isEqualTo(new MemberProfile(1L, AccountType.LOCAL, "tester01", "tester01@example.com",
                true, "닉네임1", MemberRole.USER, AccountStatus.ACTIVE));
        assertThat(sessions.revoked).isEmpty();
    }

    @Test
    void activeGoogleMemberHasNoUsername() { // MB-T055
        members.add(ProfileFakes.google(2L, AccountStatus.ACTIVE));

        MemberProfile profile = service.getMyProfile(me(2L));

        assertThat(profile.accountType()).isEqualTo(AccountType.GOOGLE);
        assertThat(profile.username()).isNull();
        assertThat(profile.email()).isEqualTo("google2@example.com");
    }

    @Test
    void disabledMemberLosesThisSessionAndIsForbidden() { // MB-T056
        members.add(ProfileFakes.local(3L, "tester03", AccountStatus.DISABLED));

        assertThatThrownBy(() -> service.getMyProfile(me(3L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.ACCOUNT_DISABLED));
        assertThat(sessions.revoked).containsExactly(SID + ":3");
    }

    @Test
    void missingOrPendingMemberLosesThisSessionAndIsUnauthenticated() { // MB-T056
        members.add(ProfileFakes.local(4L, "tester04", AccountStatus.PENDING));

        for (long id : new long[] {4L, 99L}) {
            assertThatThrownBy(() -> service.getMyProfile(me(id)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
        }
        assertThat(sessions.revoked).containsExactly(SID + ":4", SID + ":99");
    }

    @Test
    void sessionStoreFailureDuringRevokeIsServiceUnavailable() { // MB-T056: 폐기를 확인할 수 없으면 503(MB-D019)
        members.add(ProfileFakes.local(3L, "tester03", AccountStatus.DISABLED));
        sessions.revokeFailure = new LoginSessionStoreUnavailableException("down", new IllegalStateException("down"));

        assertThatThrownBy(() -> service.getMyProfile(me(3L)))
                .isInstanceOf(LoginSessionStoreUnavailableException.class);
    }

    @Test
    void activeMemberChangesNickname() { // MB-T057
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));

        MemberProfile profile = service.changeNickname(me(1L), "새 닉네임");

        assertThat(profile.nickname()).isEqualTo("새 닉네임");
        assertThat(profile.username()).isEqualTo("tester01");
        assertThat(members.nicknameUpdates).containsExactly("1:새 닉네임");
    }

    @Test
    void committedNicknameChangeIsReturnedEvenIfDisabledRightAfter() { // MB-T057: 바뀐 것을 403으로 알리지 않는다
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));
        members.afterNicknameUpdate = () -> org.springframework.test.util.ReflectionTestUtils.setField(
                members.byId.get(1L), "accountStatus", AccountStatus.DISABLED);

        MemberProfile profile = service.changeNickname(me(1L), "새 닉네임");

        assertThat(profile.nickname()).isEqualTo("새 닉네임");
        assertThat(sessions.revoked).isEmpty();
    }

    @Test
    void nicknameChangeRetriesOnceWhenMemberBecameActiveAgain() { // MB-T057: 0행이었는데 다시 ACTIVE면 한 번 더
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));
        members.forcedUpdateRows.push(1);
        members.forcedUpdateRows.push(0);

        MemberProfile retried = service.changeNickname(me(1L), "새 닉네임");

        assertThat(retried.memberId()).isEqualTo(1L);
        assertThat(retried.nickname()).isEqualTo("새 닉네임");
        assertThat(members.nicknameUpdates).containsExactly("1:새 닉네임"); // 두 번째 시도에서 실제로 바뀌었다

        members.forcedUpdateRows.push(0);
        members.forcedUpdateRows.push(0);
        assertThatThrownBy(() -> service.changeNickname(me(1L), "다른 닉네임"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sessions.revoked).isEmpty();
    }

    @Test
    void memberDeletedBeforeTheRetryIsUnauthenticatedAndLosesSession() { // MB-T057·MB-T056: 재시도 실패 원인이 삭제면 401
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));
        members.forcedUpdateRows.push(0);
        members.beforeNicknameUpdate = call -> {
            if (call == 2) { // 재시도 직전에 삭제된다
                members.byId.remove(1L);
            }
        };

        assertThatThrownBy(() -> service.changeNickname(me(1L), "새 닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
        assertThat(sessions.revoked).containsExactly(SID + ":1");
        assertThat(members.nicknameUpdates).isEmpty();
    }

    @Test
    void memberDisabledBeforeTheRetryIsForbiddenAndLosesSession() { // MB-T057·MB-T056: 재시도 실패 원인이 정지면 403
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));
        members.forcedUpdateRows.push(0); // 첫 UPDATE는 경합으로 0행, 그 뒤 상태 확인에서는 ACTIVE
        members.beforeNicknameUpdate = call -> {
            if (call == 2) { // 재시도 직전에 정지된다
                org.springframework.test.util.ReflectionTestUtils.setField(
                        members.byId.get(1L), "accountStatus", AccountStatus.DISABLED);
            }
        };

        assertThatThrownBy(() -> service.changeNickname(me(1L), "새 닉네임"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.ACCOUNT_DISABLED));
        assertThat(sessions.revoked).containsExactly(SID + ":1");
        assertThat(members.nicknameUpdates).isEmpty();
    }

    @Test
    void disabledOrMissingMemberCannotChangeNickname() { // MB-T057
        members.add(ProfileFakes.local(3L, "tester03", AccountStatus.DISABLED));

        assertThatThrownBy(() -> service.changeNickname(me(3L), "바꾼이름"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.ACCOUNT_DISABLED));
        assertThatThrownBy(() -> service.changeNickname(me(99L), "바꾼이름"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
        assertThat(members.nicknameUpdates).isEmpty();
        assertThat(members.byId.get(3L).getNickname()).isEqualTo("닉네임3");
        assertThat(sessions.revoked).containsExactly(SID + ":3", SID + ":99");
    }
}
