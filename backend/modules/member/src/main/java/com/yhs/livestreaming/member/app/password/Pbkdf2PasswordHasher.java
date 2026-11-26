package com.yhs.livestreaming.member.app.password;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 비밀번호 해시(MB-D006): NFC 정규화 → UTF-8 → PBKDF2-HMAC-SHA256(Salt 16바이트, 출력 256비트) → HMAC-SHA256(pepper).
 *
 * <p>저장 포맷: {@code pbkdf2-sha256$v1$nfc$i=<반복 횟수>$k=<키 ID>$<Salt Base64>$<해시 Base64>}. 중간 PBKDF2 결과는
 * 저장하지 않고 쓴 뒤 지운다. JDK의 {@code PBKDF2WithHmacSHA256}은 비밀번호 문자를 UTF-8로 인코딩한다.
 */
public class Pbkdf2PasswordHasher implements PasswordHasher {

    static final String ALGORITHM = "pbkdf2-sha256";
    static final String FORMAT_VERSION = "v1";
    static final String NORMALIZATION = "nfc";
    static final int SALT_BYTES = 16;
    static final int HASH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();

    private final byte[] pepper;
    private final String keyId;
    private final int iterations;

    public Pbkdf2PasswordHasher(MemberPasswordProperties properties) {
        this.pepper = properties.pepperKeyBytes();
        this.keyId = properties.pepperKeyId();
        this.iterations = properties.iterations();
    }

    @Override
    public String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = peppered(rawPassword, salt, iterations);
        return String.join("$", ALGORITHM, FORMAT_VERSION, NORMALIZATION, "i=" + iterations, "k=" + keyId,
                ENCODER.encodeToString(salt), ENCODER.encodeToString(hash));
    }

    /** 저장된 해시와 비교한다. 포맷이 잘못됐거나 키 ID가 다르면 false다. 비교는 일정 시간으로 한다. */
    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null) {
            return false;
        }
        Optional<Parsed> parsed = parse(storedHash);
        if (parsed.isEmpty() || !keyId.equals(parsed.get().keyId())) {
            return false;
        }
        byte[] candidate = peppered(rawPassword, parsed.get().salt(), parsed.get().iterations());
        return MessageDigest.isEqual(candidate, parsed.get().hash());
    }

    /** 반복 횟수·키 ID가 현재 설정과 다르거나 포맷을 읽을 수 없으면 재해시 대상이다. 재해시는 로그인(BE-03)에서 한다. */
    @Override
    public boolean needsRehash(String storedHash) {
        return parse(storedHash)
                .map(p -> p.iterations() != iterations || !keyId.equals(p.keyId()))
                .orElse(true);
    }

    private byte[] peppered(String rawPassword, byte[] salt, int rounds) {
        String normalized = PasswordNormalization.nfc(rawPassword);
        char[] chars = normalized.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, rounds, HASH_BITS);
        byte[] derived = null;
        try {
            derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return mac.doFinal(derived);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("비밀번호 해시를 계산할 수 없다", ex);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
            if (derived != null) {
                Arrays.fill(derived, (byte) 0);
            }
        }
    }

    private static Optional<Parsed> parse(String stored) {
        if (stored == null) {
            return Optional.empty();
        }
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 7 || !ALGORITHM.equals(parts[0]) || !FORMAT_VERSION.equals(parts[1])
                || !NORMALIZATION.equals(parts[2]) || !parts[3].startsWith("i=") || !parts[4].startsWith("k=")) {
            return Optional.empty();
        }
        try {
            int rounds = Integer.parseInt(parts[3].substring(2));
            String storedKeyId = parts[4].substring(2);
            byte[] salt = Base64.getDecoder().decode(parts[5]);
            byte[] hash = Base64.getDecoder().decode(parts[6]);
            if (rounds < 1 || storedKeyId.isEmpty() || salt.length < SALT_BYTES || hash.length != HASH_BITS / 8) {
                return Optional.empty();
            }
            return Optional.of(new Parsed(rounds, storedKeyId, salt, hash));
        } catch (IllegalArgumentException ex) { // NumberFormatException 포함
            return Optional.empty();
        }
    }

    private record Parsed(int iterations, String keyId, byte[] salt, byte[] hash) {
    }
}
