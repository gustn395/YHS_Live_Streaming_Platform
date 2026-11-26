package com.yhs.livestreaming.member.app.otp;

import java.time.Instant;
import java.util.Optional;

/**
 * OTP Challenge 저장 포트(MB-D015~D017). 제한 검사·카운터·TTL·검증·소비를 저장소에서 원자적으로 한다. OTP 원문은 저장하지 않는다.
 * 저장소를 쓸 수 없으면 {@code LoginSessionStoreUnavailableException}(503)을 던진다(MB-D019).
 */
public interface OtpChallengeStore {

    /** 새 로그인의 Challenge 발급. 회원 단위 간격·발급 수·실패 합산을 검사하고, 통과하면 이전 Challenge를 무효화한다. */
    OtpStoreResult issueForLogin(NewOtpChallenge challenge, Instant now);

    /** 현재(교체되지 않았고 절대 만료 전) Challenge. */
    Optional<OtpChallengeSnapshot> find(String challengeId);

    /**
     * 같은 Challenge 안에서 OTP를 다시 발급한다(version + 1, 시도 수 초기화, Challenge 만료는 그대로). 새 OTP는 발송 성공이
     * 확인될 때까지({@link #confirmDelivered}) 검증할 수 없다. 숫자 지문은 최근 두 개(이번 것과 직전 것)를 남긴다.
     */
    OtpStoreResult resend(OtpChallengeSnapshot current, String newOtpHmac, String newCodeDigest, String keyId,
                          Instant otpExpiresAt, Instant now);

    /** OTP 검증. 성공하면 Challenge를 소비한다. version이 다르면 실패로 세지 않고 {@code VERSION_CONFLICT}. */
    OtpStoreResult verify(OtpChallengeSnapshot current, String candidateHmac, Instant now);

    /**
     * 메일 발송 실패 보상. 같은 version일 때만: 로그인이면 Challenge 삭제, 재발송이면 그 OTP 무효. 숫자 지문은 지우지 않는다
     * (실패한 메일도 전달됐을 수 있다). 카운터·간격은 되돌리지 않는다. 보상이 실패해도 확인되지 않은 OTP는 검증되지 않는다.
     *
     * @return 보상했으면 true(이후에 생긴 OTP·Challenge는 건드리지 않음)
     */
    boolean deliveryFailed(String challengeId, int version, DeliveryStage stage);

    /**
     * SMTP가 메일을 받았음(발송 성공)을 기록해 그 version의 OTP를 검증 가능하게 한다(MB-D016). 같은 version일 때만 반영한다.
     *
     * @return 반영했으면 true. Challenge가 없거나 version이 바뀌었으면 false(그 OTP는 끝까지 검증할 수 없다)
     */
    boolean confirmDelivered(String challengeId, int version);

    enum DeliveryStage {
        LOGIN,
        RESEND
    }
}
