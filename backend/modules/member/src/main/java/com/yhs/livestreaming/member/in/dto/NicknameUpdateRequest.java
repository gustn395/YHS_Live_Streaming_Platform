package com.yhs.livestreaming.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 닉네임 수정 요청(MB-D028). 규칙은 회원가입과 같다(MB-D008): 앞뒤 공백 제거 뒤 2~20자(code point), 제어·서식 문자 금지,
 * 중복 허용. 닉네임 밖의 필드(username·email 등)는 받지 않는다.
 */
@Schema(description = "닉네임 수정 요청")
public record NicknameUpdateRequest(
        @Schema(description = "새 닉네임. 2~20자(code point), 중복 허용, 제어·서식 문자 금지", example = "새 닉네임")
        @NotBlank
        @Pattern(regexp = NicknameFormat.REGEXP, message = NicknameFormat.MESSAGE)
        String nickname) {

    public NicknameUpdateRequest {
        nickname = nickname == null ? null : nickname.strip();
    }
}
