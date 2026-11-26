package com.yhs.livestreaming.member.in.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** OTP 최종 인증 성공 응답(MB-D012~D014). 토큰은 본문에 넣지 않고 HttpOnly 쿠키로만 준다. */
@Schema(description = "로그인 완료")
public record LoginSuccessResponse(
        @Schema(description = "회원 ID") long memberId,
        @Schema(description = "회원 역할", example = "USER") String role,
        @Schema(description = "Access Token 만료 시각(UTC)") Instant accessTokenExpiresAt) {
}
