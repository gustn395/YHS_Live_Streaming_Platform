package com.yhs.livestreaming.member.app.otp;

import java.time.Instant;

/**
 * 1차 인증 성공 결과(MB-D015): OTP Challenge. {@code challengeId}는 HttpOnly 쿠키로만 내보낸다.
 *
 * @param challengeId        Challenge ID(쿠키 값)
 * @param otpExpiresAt       OTP 만료(응답 본문)
 * @param challengeExpiresAt Challenge 절대 만료(쿠키 Max-Age)
 */
public record LoginChallenge(String challengeId, Instant otpExpiresAt, Instant challengeExpiresAt) {

    @Override
    public String toString() {
        return "LoginChallenge[challengeId=****, otpExpiresAt=" + otpExpiresAt + ", challengeExpiresAt="
                + challengeExpiresAt + "]";
    }
}
