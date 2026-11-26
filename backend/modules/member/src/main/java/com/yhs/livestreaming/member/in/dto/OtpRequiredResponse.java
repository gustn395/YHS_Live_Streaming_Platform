package com.yhs.livestreaming.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 1차 인증 성공·재발송 응답(MB-D015): OTP 확인 대기. Challenge ID는 본문에 넣지 않는다(HttpOnly 쿠키). */
@Schema(description = "OTP 확인 대기")
public record OtpRequiredResponse(
        @Schema(description = "항상 OTP_REQUIRED", example = "OTP_REQUIRED") String status,
        @Schema(description = "OTP 만료 시각(UTC)") Instant otpExpiresAt) {

    public static OtpRequiredResponse until(Instant otpExpiresAt) {
        return new OtpRequiredResponse("OTP_REQUIRED", otpExpiresAt);
    }
}
