package com.yhs.livestreaming.member.app.otp;

import com.yhs.livestreaming.member.domain.Member;

/** 1차 인증을 통과한 회원의 OTP Challenge를 시작한다(발급 + 메일). 구현은 {@code OtpService}다. */
@FunctionalInterface
public interface OtpChallengeStarter {

    /**
     * @param deliveryEmail OTP를 보낼 주소. Challenge에 묶여 재발송도 이 주소로 간다(MB-D025)
     */
    LoginChallenge startForLogin(Member member, String deliveryEmail);
}
