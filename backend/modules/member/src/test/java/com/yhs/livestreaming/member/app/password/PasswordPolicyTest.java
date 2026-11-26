package com.yhs.livestreaming.member.app.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/** MB-T006: 비밀번호 정책(MB-D004, MB-D005). 유출 비밀번호 검사는 HIBP 어댑터가 따로 한다(MB-D010). */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsFifteenCodePoints() {
        assertThat(policy.check("abcdefghijklmno", "tester01")).isEmpty();
    }

    @Test
    void rejectsFourteenCodePoints() {
        assertThat(policy.check("abcdefghijklmn", "tester01")).containsExactly(PasswordViolation.TOO_SHORT);
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        String fourteenEmoji = "😀".repeat(14); // UTF-16 단위로는 28이지만 code point는 14
        assertThat(policy.check(fourteenEmoji, "tester01")).containsExactly(PasswordViolation.TOO_SHORT);
        assertThat(policy.check("😀".repeat(15), "tester01")).isEmpty();
    }

    @Test
    void countsLengthAfterNfcNormalization() {
        String decomposed = Normalizer.normalize("é".repeat(14), Normalizer.Form.NFD); // NFD로는 28 code point
        assertThat(policy.check(decomposed, "tester01")).containsExactly(PasswordViolation.TOO_SHORT);
    }

    @Test
    void rejectsMoreThanSixtyFourCodePoints() {
        assertThat(policy.check("a".repeat(64), "tester01")).isEmpty();
        assertThat(policy.check("a".repeat(65), "tester01")).containsExactly(PasswordViolation.TOO_LONG);
    }

    @Test
    void keepsSpacesAndDoesNotTrim() {
        String withSpaces = "  correct horse  "; // 앞뒤 공백 포함 17자
        assertThat(policy.check(withSpaces, "tester01")).isEmpty();
        assertThat(policy.check("  abcdefghijk  ", "tester01")).isEmpty(); // Trim하면 11자지만 그대로 15자
    }

    @Test
    void rejectsPasswordSameAsUsernameIgnoringCase() {
        assertThat(policy.check("Long_Username_123", "long_username_123"))
                .containsExactly(PasswordViolation.CONTEXT_WORD);
    }

    @Test
    void rejectsUsernameDerivatives() {
        assertThat(policy.check("long_username_1!!!", "long_username_1")).contains(PasswordViolation.CONTEXT_WORD);
        assertThat(policy.check("1_emanresu_gnol", "long_username_1")).contains(PasswordViolation.CONTEXT_WORD);
    }

    @Test
    void rejectsServiceNameDerivatives() {
        assertThat(policy.check("yhslivestreaming2026", "tester01")).contains(PasswordViolation.CONTEXT_WORD);
        assertThat(policy.check("YHS Live Streaming!", "tester01")).contains(PasswordViolation.CONTEXT_WORD);
    }

    @Test
    void rejectsPasswordEqualToDigitsOnlyUsername() { // MB-D004: 핵심어가 빈 문자열이어도 username과 같으면 거부
        String digits = "19736482051983647201";
        assertThat(policy.check(digits, digits)).contains(PasswordViolation.CONTEXT_WORD);
        assertThat(policy.check("1234_5678_9012_3", "1234_5678_9012_3")).contains(PasswordViolation.CONTEXT_WORD);
    }

    @Test
    void rejectsReversedOrDecoratedDigitsOnlyUsername() { // MB-D005: username 파생어(뒤집기·기호 덧붙이기)
        assertThat(policy.check("76543210987654321", "12345678901234567")).contains(PasswordViolation.CONTEXT_WORD);
        assertThat(policy.check("12345678901234567!", "12345678901234567")).contains(PasswordViolation.CONTEXT_WORD);
        assertThat(policy.check("98765432109876543", "12345678901234567")).isEmpty(); // 다른 숫자열은 허용
    }

    @Test
    void stripsTrailingDigitsByCodePoint() { // U+1D7D9(보충 평면 숫자) 접미사도 숫자로 떼어 낸다
        String supplementaryOne = new String(Character.toChars(0x1D7D9));
        assertThat(policy.check("yhslivestreaming" + supplementaryOne, "tester01")).contains(PasswordViolation.CONTEXT_WORD);
        assertThat(policy.check("yhslivestreaming" + supplementaryOne + "7", "tester01")).contains(PasswordViolation.CONTEXT_WORD);
        assertThat(policy.check("yhslivestreaming2026", "tester01")).contains(PasswordViolation.CONTEXT_WORD); // ASCII 숫자
    }
}
