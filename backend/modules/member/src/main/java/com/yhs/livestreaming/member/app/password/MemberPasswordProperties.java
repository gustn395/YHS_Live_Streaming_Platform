package com.yhs.livestreaming.member.app.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 비밀번호 해시 설정(MB-D006, MB-D007). pepper 키는 기본값 없는 환경변수({@code MEMBER_PASSWORD_PEPPER_KEY},
 * {@code MEMBER_PASSWORD_PEPPER_KEY_ID})로만 받는다. 키가 없거나 Base64가 아니거나 256비트가 아니면 바인딩에 실패해
 * 애플리케이션이 뜨지 않는다. 오류 메시지에는 키 값을 넣지 않는다.
 *
 * @param pepperKey         Base64로 인코딩한 256비트 키
 * @param pepperKeyId       해시 포맷에 남기는 키 ID(키 교체 대비)
 * @param iterations        PBKDF2 반복 횟수(기본 600,000)
 */
@Validated
@ConfigurationProperties("member.password")
public record MemberPasswordProperties(
        @NotBlank String pepperKey,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,32}") String pepperKeyId,
        Integer iterations) {

    public static final int DEFAULT_ITERATIONS = 600_000;
    static final int PEPPER_KEY_BYTES = 32;

    public MemberPasswordProperties {
        if (iterations == null) {
            iterations = DEFAULT_ITERATIONS;
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("member.password.iterations는 1 이상이어야 한다");
        }
        if (pepperKey != null && !pepperKey.isBlank()) {
            int length = decode(pepperKey).length;
            if (length != PEPPER_KEY_BYTES) {
                throw new IllegalArgumentException(
                        "member.password.pepper-key는 256비트여야 한다(현재 " + length * 8 + "비트)");
            }
        }
    }

    /** 디코딩한 pepper 키. 호출할 때마다 새 배열을 돌려준다. */
    public byte[] pepperKeyBytes() {
        return decode(pepperKey);
    }

    private static byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            // 원래 예외 메시지에는 잘못된 문자가 들어 있어 원인으로 연결하지 않는다
            throw new IllegalArgumentException("member.password.pepper-key가 Base64 형식이 아니다");
        }
    }

    @Override
    public String toString() {
        return "MemberPasswordProperties[pepperKey=****, pepperKeyId=" + pepperKeyId
                + ", iterations=" + iterations + "]";
    }
}
