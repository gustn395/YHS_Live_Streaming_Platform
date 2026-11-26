package com.yhs.livestreaming.member.app.otp;

import com.yhs.livestreaming.member.domain.Member;

/** 1차 인증을 통과한 회원의 OTP Challenge를 시작한다(발급 + 메일). 구현은 {@code OtpService}다. */
@FunctionalInterface
public interface OtpChallengeStarter {

    LoginChallenge startForLogin(Member member);
}
