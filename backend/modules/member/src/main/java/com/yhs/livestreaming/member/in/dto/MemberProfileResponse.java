package com.yhs.livestreaming.member.in.dto;

import com.yhs.livestreaming.member.app.profile.MemberProfile;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

/** 내 정보(MB-D028). 비밀번호 해시·OAuth 식별자·가입 시각 같은 내부 값은 담지 않는다. */
@Schema(description = "로그인한 회원 본인의 정보")
public record MemberProfileResponse(
        @Schema(description = "회원 ID", example = "1") long memberId,
        @Schema(description = "계정 유형", example = "LOCAL") AccountType accountType,
        @Schema(description = "로그인 아이디. GOOGLE 회원은 null", example = "tester01", nullable = true) String username,
        @Schema(description = "이메일", example = "tester@example.com") String email,
        @Schema(description = "이메일 확인 여부", example = "true") boolean emailVerified,
        @Schema(description = "닉네임", example = "테스터") String nickname,
        @Schema(description = "회원 역할", example = "USER") MemberRole role,
        @Schema(description = "계정 상태", example = "ACTIVE") AccountStatus accountStatus) {

    public static MemberProfileResponse from(MemberProfile profile) {
        return new MemberProfileResponse(profile.memberId(), profile.accountType(), profile.username(),
                profile.email(), profile.emailVerified(), profile.nickname(), profile.role(),
                profile.accountStatus());
    }
}
