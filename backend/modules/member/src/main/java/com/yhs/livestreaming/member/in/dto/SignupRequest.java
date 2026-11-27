package com.yhs.livestreaming.member.in.dto;

import com.yhs.livestreaming.member.in.ValidSignupPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * 회원가입 요청(MB-D008). 생성자에서 정규화한 값으로 검증한다: username·이메일 소문자, 이메일·닉네임 앞뒤 공백 제거.
 * 비밀번호는 바꾸지 않는다(Trim 금지, MB-D004). {@link #toString()}은 비밀번호를 가린다.
 */
@ValidSignupPassword
@Schema(description = "일반 회원가입 요청")
public record SignupRequest(
        @Schema(description = "로그인 아이디. 영문 소문자·숫자·_ 4~20자(대문자는 소문자로 바꾼다)", example = "tester01")
        @NotBlank
        @Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 영문 소문자·숫자·_ 4~20자입니다.")
        String username,

        @Schema(description = "비밀번호. 10~30자(NFC 정규화 뒤 code point 기준), 구성 규칙 없음, 공백 허용", example = "correct horse battery staple")
        @NotNull
        String password,

        @Schema(description = "비밀번호 확인", example = "correct horse battery staple")
        @NotNull
        String passwordConfirm,

        @Schema(description = "이메일(소문자로 저장)", example = "tester@example.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Schema(description = "닉네임. 2~20자(code point), 중복 허용, 제어·서식 문자 금지", example = "테스터")
        @NotBlank
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]{2,20}$",
                message = "닉네임은 2~20자이고 제어 문자·보이지 않는 서식 문자를 쓸 수 없습니다.")
        String nickname) {

    private static final java.util.regex.Pattern ASCII_USERNAME_CHARS =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_]*$");

    public SignupRequest {
        // ASCII일 때만 소문자로 바꾼다. 비ASCII(예: U+212A 켈빈 기호)를 소문자로 바꾸면 ASCII 'k'가 되어 형식 검사를 우회한다.
        username = username != null && ASCII_USERNAME_CHARS.matcher(username).matches()
                ? username.toLowerCase(Locale.ROOT) : username;
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        nickname = nickname == null ? null : nickname.strip();
    }

    @Override
    public String toString() {
        return "SignupRequest[username=" + username + ", password=****, passwordConfirm=****, email=" + email
                + ", nickname=" + nickname + "]";
    }
}
