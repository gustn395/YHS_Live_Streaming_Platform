package com.yhs.livestreaming.member.in;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** MB-T051 보완: 콜백 {@code ?error=}는 공격자가 정할 수 있으므로 형식에 맞는 OAuth 오류 코드만 로그에 남긴다(MB-D023). */
class GoogleLoginFailureHandlerTest {

    @Test
    void wellFormedOAuthErrorCodesAreLoggedAsIs() {
        assertThat(GoogleLoginFailureHandler.loggableCode("access_denied")).isEqualTo("access_denied");
        assertThat(GoogleLoginFailureHandler.loggableCode("authorization_request_not_found"))
                .isEqualTo("authorization_request_not_found");
    }

    @Test
    void attackerControlledOrMalformedCodesAreReplaced() {
        assertThat(GoogleLoginFailureHandler.loggableCode("access_denied\r\nFAKE LOG LINE")).isEqualTo("invalid");
        assertThat(GoogleLoginFailureHandler.loggableCode("a".repeat(65))).isEqualTo("invalid");
        assertThat(GoogleLoginFailureHandler.loggableCode("")).isEqualTo("invalid");
        assertThat(GoogleLoginFailureHandler.loggableCode(null)).isEqualTo("invalid");
    }
}
