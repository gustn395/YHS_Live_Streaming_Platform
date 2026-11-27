package com.yhs.livestreaming.member.app.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** MB-T047: Google 이름 → 닉네임(MB-D025, MB-D008 규칙). */
class GoogleNicknameTest {

    private static String of(String name) {
        return GoogleNickname.of(name, () -> "123456");
    }

    @Test
    void trimsAndKeepsAValidName() {
        assertThat(of("  홍길동  ")).isEqualTo("홍길동");
    }

    @Test
    void removesControlAndFormatCharacters() {
        assertThat(of("Ab\u0000c​d‮")).isEqualTo("Abcd");
    }

    @Test
    void cutsToTwentyCodePointsWithoutSplittingSurrogates() {
        String emoji = "😀"; // 1 code point, 2 chars
        assertThat(of(emoji.repeat(25))).isEqualTo(emoji.repeat(20));
        assertThat(of("a".repeat(30))).hasSize(20);
    }

    @Test
    void tooShortOrMissingNameFallsBackToUserPlusSixDigits() {
        assertThat(of(null)).isEqualTo("user123456");
        assertThat(of(" ")).isEqualTo("user123456");
        assertThat(of("가")).isEqualTo("user123456");
        assertThat(of("​​")).isEqualTo("user123456");
    }

    @Test
    void cutResultIsTrimmedAgain() {
        assertThat(of("a".repeat(19) + "  bbb")).isEqualTo("a".repeat(19));
    }
}
