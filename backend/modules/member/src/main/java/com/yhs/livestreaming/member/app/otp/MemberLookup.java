package com.yhs.livestreaming.member.app.otp;

import com.yhs.livestreaming.member.domain.Member;
import java.util.Optional;

/** OTP 재발송 때 메일 주소를 얻기 위한 회원 조회. */
@FunctionalInterface
public interface MemberLookup {

    Optional<Member> findById(long memberId);
}
