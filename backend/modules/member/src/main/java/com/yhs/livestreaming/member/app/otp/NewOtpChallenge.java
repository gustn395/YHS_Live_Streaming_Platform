package com.yhs.livestreaming.member.app.otp;

import java.time.Instant;

/**
 * 1차 인증 성공 때 만드는 OTP Challenge(MB-D015). OTP 원문은 담지 않고 HMAC·숫자 지문만 담는다.
 *
 * @param challengeId        SecureRandom 불투명 난수(쿠키 값)
 * @param memberId           회원 ID
 * @param otpHmac            검증용 HMAC(OTP 키, challengeId:version:code), version=1
 * @param codeDigest         숫자 지문(OTP 키, challengeId:code). 재발송 때 같은 숫자를 다시 보내지 않으려고 둔다
 * @param keyId              OTP HMAC 키 ID
 * @param otpExpiresAt       OTP 만료(발급 + 5분)
 * @param challengeExpiresAt Challenge 절대 만료(발급 + 10분)
 */
public record NewOtpChallenge(String challengeId, long memberId, String otpHmac, String codeDigest, String keyId,
                              Instant otpExpiresAt, Instant challengeExpiresAt, String deliveryEmail) {

    @Override
    public String toString() {
        return "NewOtpChallenge[challengeId=****, memberId=" + memberId + ", otpHmac=****, codeDigest=****, keyId="
                + keyId + ", otpExpiresAt=" + otpExpiresAt + ", challengeExpiresAt=" + challengeExpiresAt
                + ", deliveryEmail=****]";
    }
}
