package com.yhs.livestreaming.member.app.otp;

import java.time.Duration;

/**
 * OTP 저장소 처리 결과(MB-D015, MB-D017).
 *
 * @param status     결과
 * @param version    발급·재발송 성공 시 새 version
 * @param memberId   검증 성공 시 회원 ID
 * @param retryAfter 제한(429)일 때 남은 시간
 */
public record OtpStoreResult(Status status, int version, long memberId, Duration retryAfter) {

    public enum Status {
        OK,
        /** Challenge 없음·만료·교체됨 → 다시 로그인 */
        INVALID_CHALLENGE,
        /** 회원 단위 60초 발급 간격 */
        INTERVAL_LIMITED,
        /** 회원 단위 시간당·24시간 발급 수 */
        ISSUE_LIMITED,
        /** 회원 단위 24시간 검증 실패 합산 */
        FAILURE_LIMITED,
        /** 계산에 쓴 version이 현재와 다름(재발송과 경합). 실패로 세지 않는다 */
        VERSION_CONFLICT,
        /** OTP 만료·무효(재발송 필요) */
        EXPIRED,
        /** 불일치(실패 1회 기록) */
        MISMATCH,
        /** 이번 불일치로 OTP당 시도 수를 다 씀(OTP 무효) */
        EXHAUSTED
    }

    public static OtpStoreResult of(Status status) {
        return new OtpStoreResult(status, 0, 0, Duration.ZERO);
    }
}
