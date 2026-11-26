package com.yhs.livestreaming.member.in.dto;

import com.yhs.livestreaming.member.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 결과. 토큰은 발급하지 않는다(MB-D003)")
public record SignupResponse(
        @Schema(description = "회원 ID", example = "1") long memberId,
        @Schema(description = "계정 상태. 가입 직후는 PENDING", example = "PENDING") AccountStatus accountStatus) {
}
