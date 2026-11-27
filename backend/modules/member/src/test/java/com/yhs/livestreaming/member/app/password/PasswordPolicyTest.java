package com.yhs.livestreaming.member.app.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/** MB-T006: 비밀번호 정책(MB-D026 길이, MB-D004, MB-D005). 유출 비밀번호 검사는 HIBP 어댑터가 따로 한다(MB-D010). */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsTenCodePoints() { // MB-D026 하한 경계
        assertThat(policy.check("abcdefghij", "tester01")).isEmpty();
    }

    @Test
    void rejectsNineCodePoints() {
        assertThat(policy.check("abcdefghi", "tester01")).containsExactly(PasswordViolation.TOO_SHORT);
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        String nineEmoji = "😀".repeat(9); // UTF-16 단위로는 18이지만 code point는 9
        assertThat(policy.check(nineEmoji, "tester01")).containsExactly(PasswordViolation.TOO_SHORT);
        assertThat(policy.check("😀".repeat(10), "tester01")).isEmpty();
        assertThat(policy.check("😀".repeat(30), "tester01")).isEmpty(); // UTF-16 단위 60
        assertThat(policy.check("😀".repeat(31), "tester01")).containsExactly(PasswordViolation.TOO_LONG);
    }

    @Test
    void countsLengthAfterNfcNormalization() {
        String nineDecomposed = Normalizer.normalize("é".repeat(9), Normalizer.Form.NFD); // NFD로는 18 code point
        assertThat(policy.check(nineDecomposed, "tester01")).containsExactly(PasswordViolation.TOO_SHORT);
        String thirtyDecomposed = Normalizer.normalize("é".repeat(30), Normalizer.Form.NFD); // NFD로는 60 code point
        assertThat(policy.check(thirtyDecomposed, "tester01")).isEmpty();
    }

    @Test
    void rejectsMoreThanThirtyCodePoints() { // MB-D026 상한 경계
        assertThat(policy.check("a".repeat(30), "tester01")).isEmpty();
        assertThat(policy.check("a".repeat(31), "tester01")).containsExactly(PasswordViolation.TOO_LONG);
    }

    @Test
    void lengthMessagesStateTenAndThirty() { // MB-D026: 거부 이유 문구(입력값 없음)
        assertThat(PasswordViolation.TOO_SHORT.message()).isEqualTo("비밀번호는 10자 이상이어야 합니다.");
        assertThat(PasswordViolation.TOO_LONG.message()).isEqualTo("비밀번호는 30자 이하여야 합니다.");
    }

    @Test
    void keepsSpacesAndDoesNotTrim() {
        String withSpaces = "  correct horse  "; // 앞뒤 공백 포함 17자
        assertThat(policy.check(withSpaces, "tester01")).isEmpty();
        assertThat(policy.check("  abcdef  ", "tester01")).isEmpty(); // Trim하면 6자지만 그대로 10자
        assertThat(policy.check("abcdefgh ", "tester01")).containsExactly(PasswordViolation.TOO_SHORT); // 공백 포함 9자
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
