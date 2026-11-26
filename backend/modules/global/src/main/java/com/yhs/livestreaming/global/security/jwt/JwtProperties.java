package com.yhs.livestreaming.global.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Access Token 서명 키(MB-D007, MB-D012). 기본값 없는 환경변수 {@code AUTH_JWT_KEY}(Base64 256비트)·{@code AUTH_JWT_KEY_ID}로만
 * 받는다. 키가 없거나 Base64가 아니거나 256비트가 아니거나 키 ID가 없으면 바인딩에 실패해 애플리케이션이 뜨지 않는다.
 * 오류 메시지·{@link #toString()}에는 키 값을 넣지 않는다.
 *
 * @param key   Base64로 인코딩한 256비트 HMAC 키
 * @param keyId JWT 헤더 {@code kid}에 넣는 키 ID
 */
@Validated
@ConfigurationProperties("auth.jwt")
public record JwtProperties(
        @NotBlank String key,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String keyId) {

    static final int KEY_BYTES = 32;

    public JwtProperties {
        if (key != null && !key.isBlank()) {
            int length = decode(key).length;
            if (length != KEY_BYTES) {
                throw new IllegalArgumentException("auth.jwt.key는 256비트여야 한다(현재 " + length * 8 + "비트)");
            }
        }
    }

    /** 디코딩한 키. 호출할 때마다 새 배열을 돌려준다. */
    public byte[] keyBytes() {
        return decode(key);
    }

    private static byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            // 원래 예외 메시지에는 잘못된 문자가 들어 있어 원인으로 연결하지 않는다
            throw new IllegalArgumentException("auth.jwt.key가 Base64 형식이 아니다");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[key=****, keyId=" + keyId + "]";
    }
}
