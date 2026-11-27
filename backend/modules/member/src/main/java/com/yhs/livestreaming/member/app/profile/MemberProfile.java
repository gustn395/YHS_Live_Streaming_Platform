package com.yhs.livestreaming.member.app.profile;

import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.MemberRole;

/** 로그인한 회원 본인의 정보(MB-D028). GOOGLE 회원은 username이 null이다. */
public record MemberProfile(long memberId, AccountType accountType, String username, String email,
                            boolean emailVerified, String nickname, MemberRole role, AccountStatus accountStatus) {
}
