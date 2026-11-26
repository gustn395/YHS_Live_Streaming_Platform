package com.yhs.livestreaming.member.app.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OTP HMAC 키(MB-D007, MB-D015). 기본값 없는 환경변수 {@code MEMBER_OTP_HMAC_KEY}(Base64 256비트)·
 * {@code MEMBER_OTP_HMAC_KEY_ID}로만 받는다. 키가 없거나 형식이 틀리거나 키 ID가 없으면 기동에 실패하고, 오류 메시지에 키 값을
 * 넣지 않는다. pepper·JWT 키와 다른 키를 쓴다.
 */
@Validated
@ConfigurationProperties("member.otp")
public record MemberOtpProperties(
        @NotBlank String hmacKey,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String hmacKeyId) {

    static final int KEY_BYTES = 32;

    public MemberOtpProperties {
        if (hmacKey != null && !hmacKey.isBlank()) {
            int length = decode(hmacKey).length;
            if (length != KEY_BYTES) {
                throw new IllegalArgumentException("member.otp.hmac-key는 256비트여야 한다(현재 " + length * 8 + "비트)");
            }
        }
    }

    public byte[] hmacKeyBytes() {
        return decode(hmacKey);
    }

    private static byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("member.otp.hmac-key가 Base64 형식이 아니다");
        }
    }

    @Override
    public String toString() {
        return "MemberOtpProperties[hmacKey=****, hmacKeyId=" + hmacKeyId + "]";
    }
}
