package com.yhs.livestreaming.member.app.otp;

import java.time.Instant;

/** 재발송 결과: 새 OTP 만료 시각(Challenge 절대 만료를 넘지 않는다). */
public record OtpResent(Instant otpExpiresAt) {
}
