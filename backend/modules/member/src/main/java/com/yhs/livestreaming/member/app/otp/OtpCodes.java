package com.yhs.livestreaming.member.app.otp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.function.IntSupplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * OTP 생성과 HMAC(MB-D015). OTP는 SecureRandom 0~9999를 4자리 문자열("0000"~"9999")로 만든다. 저장소에는
 * HMAC-SHA256(OTP 키, {@code challengeId:version:code})의 Base64url만 둔다. version을 넣어 재발송 전 OTP가 새 version으로
 * 통과하지 못하게 한다.
 */
public class OtpCodes {

    static final int BOUND = 10_000;

    private final byte[] key;
    private final String keyId;
    private final IntSupplier random;

    public OtpCodes(MemberOtpProperties properties) {
        this(properties, secureRandom());
    }

    /** 테스트용: 난수 공급원을 바꾼다. 공급값은 0~9999 범위여야 한다. */
    OtpCodes(MemberOtpProperties properties, IntSupplier random) {
        this.key = properties.hmacKeyBytes();
        this.keyId = properties.hmacKeyId();
        this.random = random;
    }

    public String newCode() {
        int value = random.getAsInt();
        if (value < 0 || value >= BOUND) {
            throw new IllegalStateException("OTP 범위 밖 값");
        }
        // Locale.ROOT: 기본 로케일이 다른 숫자 체계(예: 태국 숫자)여도 ASCII 0~9로 만든다
        return String.format(Locale.ROOT, "%04d", value);
    }

    /** 검증용 HMAC: challengeId·version·code에 묶인다. */
    public String hmac(String challengeId, int version, String code) {
        return mac(challengeId + ":" + version + ":" + code);
    }

    /**
     * version에 묶이지 않은 숫자 지문: 회원이 실제로 받은 4자리를 기억해, 재발송 때 같은 숫자를 다시 보내지 않는 데만 쓴다.
     * 검증에는 쓰지 않는다.
     */
    public String digest(String challengeId, String code) {
        return mac(challengeId + ":code:" + code);
    }

    private String mac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("OTP HMAC을 계산할 수 없다", ex);
        }
    }

    public String keyId() {
        return keyId;
    }

    private static IntSupplier secureRandom() {
        SecureRandom random = new SecureRandom();
        return () -> random.nextInt(BOUND);
    }
}
