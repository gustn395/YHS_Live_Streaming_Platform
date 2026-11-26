package com.yhs.livestreaming.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * GL-T007: 인증 주체는 식별에 필요한 값(memberId·role·sessionId)이 모두 유효할 때만 만들어진다.
 * 잘못된 인증 주체가 SecurityContext에 들어가 다른 모듈로 전달되는 것을 막는다(G-D006).
 */
class AuthenticatedMemberTest {

    @Test
    void keepsIdentityValues() {
        AuthenticatedMember member = new AuthenticatedMember(42L, "USER", "session-1");

        assertThat(member.memberId()).isEqualTo(42L);
        assertThat(member.role()).isEqualTo("USER");
        assertThat(member.sessionId()).isEqualTo("session-1");
    }

    @Test
    void rejectsNonPositiveMemberId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthenticatedMember(0L, "USER", "session-1"));
    }

    @Test
    void rejectsBlankRole() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthenticatedMember(1L, " ", "session-1"));
    }

    @Test
    void rejectsBlankSessionId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthenticatedMember(1L, "USER", ""));
    }
}
