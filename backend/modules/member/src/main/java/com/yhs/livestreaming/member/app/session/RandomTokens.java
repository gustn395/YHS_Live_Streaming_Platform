package com.yhs.livestreaming.member.app.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 불투명 난수(MB-D014, MB-D015): SecureRandom 256비트(32바이트) → Base64url 패딩 없음(43자). Session ID·Refresh Token·
 * OTP Challenge ID에 쓴다. Refresh Token은 SHA-256 hex로만 저장한다.
 */
public final class RandomTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private RandomTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256을 쓸 수 없다", ex);
        }
    }
}
