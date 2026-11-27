package com.yhs.livestreaming.member.app.otp;

import java.time.Instant;
import java.util.List;

/**
 * 현재 OTP Challenge 상태(MB-D015). 검증·재발송 HMAC을 이 {@code version}으로 계산하고, 저장소는 같은 version일 때만
 * 반영한다(재발송·검증 경합 구분).
 *
 * @param deliveryEmail OTP를 보내는 주소(발급 때 묶임, MB-D025). LOCAL은 회원 이메일, GOOGLE은 그 로그인의 Google 이메일.
 *                      null이면 이 필드가 없던 이전 Challenge라 쓸 수 없다
 * @param recentDigests 최근에 보냈거나 보내려 한 4자리 숫자 지문(최대 2개: 이번 것과 그 직전 것). 메일이 실패로 끝났어도
 *                      실제로 전달됐을 수 있으므로 지우지 않는다. 재발송 때 같은 숫자를 다시 보내지 않는 데만 쓴다
 */
public record OtpChallengeSnapshot(String challengeId, long memberId, int version, String keyId, Instant otpExpiresAt,
                                   Instant challengeExpiresAt, List<String> recentDigests, String deliveryEmail) {

    public OtpChallengeSnapshot {
        recentDigests = recentDigests == null ? List.of()
                : recentDigests.stream().filter(digest -> digest != null && !digest.isEmpty()).toList();
    }

    public OtpChallengeSnapshot(String challengeId, long memberId, int version, String keyId, Instant otpExpiresAt,
                                Instant challengeExpiresAt, String recentDigest, String deliveryEmail) {
        this(challengeId, memberId, version, keyId, otpExpiresAt, challengeExpiresAt,
                recentDigest == null ? List.of() : List.of(recentDigest), deliveryEmail);
    }

    @Override
    public String toString() {
        return "OtpChallengeSnapshot[challengeId=****, memberId=" + memberId + ", version=" + version + ", keyId="
                + keyId + ", otpExpiresAt=" + otpExpiresAt + ", challengeExpiresAt=" + challengeExpiresAt
                + ", recentDigests=****, deliveryEmail=****]";
    }
}
