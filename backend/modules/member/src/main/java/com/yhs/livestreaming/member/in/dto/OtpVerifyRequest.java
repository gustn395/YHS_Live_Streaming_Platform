package com.yhs.livestreaming.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** OTP 확인 요청(MB-D015). Challenge는 쿠키({@code __Host-yhs_otp})로 온다. {@link #toString()}은 OTP를 가린다. */
@Schema(description = "이메일 OTP 확인 요청")
public record OtpVerifyRequest(
        @Schema(description = "이메일로 받은 4자리 인증 번호", example = "0420")
        @NotNull
        @Pattern(regexp = "^[0-9]{4}$", message = "인증 번호는 숫자 4자리입니다.")
        String code) {

    @Override
    public String toString() {
        return "OtpVerifyRequest[code=****]";
    }
}
