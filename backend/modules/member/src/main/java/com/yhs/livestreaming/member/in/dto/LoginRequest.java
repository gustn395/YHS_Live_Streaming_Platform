package com.yhs.livestreaming.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * 로그인 1차 인증 요청(MB-D011). username은 회원가입과 같은 규칙으로 정규화한다(ASCII일 때만 소문자, MB-D008).
 * 비밀번호는 가입과 같이 공백만으로도 받고 Trim하지 않는다(MB-D004, {@code @NotNull}만). 형식이 맞지 않는 username·비밀번호도
 * 400으로 구분하지 않고 같은 실패(401)로 처리하며, 길이 상한은 해시 계산 남용을 막는 입력 크기 제한이다.
 * {@link #toString()}은 비밀번호를 가린다.
 */
@Schema(description = "로그인 1차 인증(아이디·비밀번호) 요청")
public record LoginRequest(
        @Schema(description = "아이디", example = "tester01")
        @NotBlank
        @Size(max = 50)
        String username,

        @Schema(description = "비밀번호", example = "correct horse battery staple")
        @NotNull
        @Size(max = 256)
        String password) {

    private static final java.util.regex.Pattern ASCII_USERNAME_CHARS =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_]*$");

    public LoginRequest {
        username = username != null && ASCII_USERNAME_CHARS.matcher(username).matches()
                ? username.toLowerCase(Locale.ROOT) : username;
    }

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}
