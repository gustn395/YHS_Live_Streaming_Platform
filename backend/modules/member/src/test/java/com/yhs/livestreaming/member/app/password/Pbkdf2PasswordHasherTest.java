package com.yhs.livestreaming.member.app.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * MB-T007: 비밀번호 해시(MB-D006). 단위 테스트라 반복 횟수를 줄인다(포맷·비교 동작은 반복 횟수와 무관하다).
 * 테스트 전용 키만 쓴다(MB-D007).
 */
class Pbkdf2PasswordHasherTest {

    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_TEST_KEY = Base64.getEncoder().encodeToString(filled(32, (byte) 7));
    private static final String PASSWORD = "correct horse battery staple";

    private final Pbkdf2PasswordHasher hasher = hasher(TEST_KEY, "k1", 1_000);

    @Test
    void hashUsesDocumentedFormatWithoutRawPassword() {
        String stored = hasher.hash(PASSWORD);

        assertThat(stored).matches("pbkdf2-sha256\\$v1\\$nfc\\$i=1000\\$k=k1\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+");
        assertThat(stored).doesNotContain(PASSWORD);
        String[] parts = stored.split("\\$");
        assertThat(Base64.getDecoder().decode(parts[5])).hasSizeGreaterThanOrEqualTo(16); // Salt 16바이트 이상
        assertThat(Base64.getDecoder().decode(parts[6])).hasSize(32);                     // 최종 HMAC 256비트
    }

    @Test
    void samePasswordGetsDifferentHashesBecauseOfSalt() {
        assertThat(hasher.hash(PASSWORD)).isNotEqualTo(hasher.hash(PASSWORD));
    }

    @Test
    void matchesOnlyTheOriginalPassword() {
        String stored = hasher.hash(PASSWORD);

        assertThat(hasher.matches(PASSWORD, stored)).isTrue();
        assertThat(hasher.matches(PASSWORD + " ", stored)).isFalse(); // Trim하지 않는다
        assertThat(hasher.matches("Correct horse battery staple", stored)).isFalse();
    }

    @Test
    void composedAndDecomposedUnicodeAreTheSamePassword() {
        String composed = Normalizer.normalize("café crème brûlée 2026", Normalizer.Form.NFC);
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo(composed);

        assertThat(hasher.matches(decomposed, hasher.hash(composed))).isTrue();
    }

    @Test
    void differentPepperDoesNotMatch() {
        String stored = hasher.hash(PASSWORD);

        assertThat(hasher(OTHER_TEST_KEY, "k1", 1_000).matches(PASSWORD, stored)).isFalse();
    }

    @Test
    void unknownKeyIdDoesNotMatch() {
        String stored = hasher.hash(PASSWORD);

        assertThat(hasher(TEST_KEY, "k2", 1_000).matches(PASSWORD, stored)).isFalse();
    }

    @Test
    void malformedStoredHashDoesNotMatch() {
        assertThat(hasher.matches(PASSWORD, "not-a-hash")).isFalse();
        assertThat(hasher.matches(PASSWORD, "pbkdf2-sha256$v1$nfc$i=abc$k=k1$AAAA$AAAA")).isFalse();
        assertThat(hasher.matches(PASSWORD, "bcrypt$v1$nfc$i=1000$k=k1$AAAA$AAAA")).isFalse();
        assertThat(hasher.matches(PASSWORD, null)).isFalse();
    }

    @Test
    void needsRehashWhenIterationsDiffer() {
        String stored = hasher.hash(PASSWORD);

        assertThat(hasher.needsRehash(stored)).isFalse();
        assertThat(hasher(TEST_KEY, "k1", 2_000).needsRehash(stored)).isTrue();
        assertThat(hasher.needsRehash("not-a-hash")).isTrue();
    }

    private static Pbkdf2PasswordHasher hasher(String key, String keyId, int iterations) {
        return new Pbkdf2PasswordHasher(new MemberPasswordProperties(key, keyId, iterations));
    }

    private static byte[] filled(int size, byte value) {
        byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}
