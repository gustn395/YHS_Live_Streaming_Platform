package com.yhs.livestreaming.member.app.otp;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.TooManyRequestsException;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStore.DeliveryStage;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 이메일 OTP 발급·재발송·검증(MB-W07, MB-D015~D017).
 *
 * <ul>
 *     <li>OTP는 4자리, 유효 5분. Challenge는 절대 10분이고 재발송으로 연장하지 않는다.</li>
 *     <li>저장소에는 HMAC(challengeId:version:code)만 둔다. 검증 HMAC은 현재 version으로 계산하고, 저장소는 같은 version일 때만
 *     반영한다. 재발송과 겹쳐 version이 바뀌었으면 실패로 세지 않고 "새 인증 번호 발송" 안내(401 AUTH_OTP_EXPIRED)를 준다.</li>
 *     <li>재발송 때 최근에 보냈거나 보내려 한 4자리(숫자 지문 최대 2개)와 같은 값이 나오면 다시 뽑는다(직전 OTP가 계속 통하는
 *     것처럼 보이지 않게). 실패한 메일도 실제로는 전달됐을 수 있어 지문을 지우지 않는다.</li>
 *     <li>저장한 OTP는 SMTP 발송 성공을 확인한 뒤에야 검증할 수 있다(발송 결과를 모르는 OTP는 끝까지 쓸 수 없다).</li>
 *     <li>메일은 저장 뒤 동기로 보낸다. 실패하면 그 version만 보상(로그인: Challenge 삭제, 재발송: 그 OTP 무효)하고 503.
 *     카운터·발급 간격은 되돌리지 않는다. 보상 자체가 실패해도(저장소 장애) 응답은 503 메일 발송 실패다.</li>
 * </ul>
 * IP 제한은 입력 계층이 먼저 건다(MB-D018). 로그에는 메일 주소·OTP·Challenge ID·SMTP 오류 원문을 남기지 않는다.
 */
public class OtpService implements OtpChallengeStarter {

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    private static final int MAX_CODE_DRAWS = 20;
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpChallengeStore store;
    private final OtpCodes codes;
    private final OtpMailSender mail;
    private final MemberLookup memberLookup;
    private final Clock clock;
    private final Supplier<String> challengeIds;
    private final Duration issueInterval;

    /** @param issueInterval 회원 단위 발급 간격(설정값). 재발송 경합에서 진 요청의 Retry-After로 쓴다. */
    public OtpService(OtpChallengeStore store, OtpCodes codes, OtpMailSender mail, MemberLookup memberLookup,
                      Clock clock, Supplier<String> challengeIds, Duration issueInterval) {
        this.store = store;
        this.codes = codes;
        this.mail = mail;
        this.memberLookup = memberLookup;
        this.clock = clock;
        this.challengeIds = challengeIds;
        this.issueInterval = issueInterval;
    }

    @Override
    public LoginChallenge startForLogin(Member member) {
        Instant now = clock.instant();
        Instant otpExpiresAt = now.plus(OTP_TTL);
        Instant challengeExpiresAt = now.plus(CHALLENGE_TTL);
        for (int attempt = 0; attempt < 2; attempt++) {
            String challengeId = challengeIds.get();
            String code = codes.newCode();
            OtpStoreResult result = store.issueForLogin(new NewOtpChallenge(challengeId, member.getId(),
                    codes.hmac(challengeId, 1, code), codes.digest(challengeId, code), codes.keyId(), otpExpiresAt,
                    challengeExpiresAt), now);
            switch (result.status()) {
                case OK -> {
                    deliver(member, code, otpExpiresAt, challengeId, result.version(), DeliveryStage.LOGIN);
                    return new LoginChallenge(challengeId, otpExpiresAt, challengeExpiresAt);
                }
                case INTERVAL_LIMITED, ISSUE_LIMITED, FAILURE_LIMITED -> throw limited(result.retryAfter());
                default -> {
                    // 새 Challenge ID 충돌: 다른 ID로 한 번 더
                }
            }
        }
        throw new LoginSessionStoreUnavailableException("OTP Challenge를 만들 수 없다", null);
    }

    public OtpResent resend(String challengeId) {
        OtpChallengeSnapshot current = store.find(challengeId).orElseThrow(OtpService::challengeInvalid);
        Member member = memberLookup.findById(current.memberId()).orElseThrow(OtpService::challengeInvalid);
        Instant now = clock.instant();
        Instant otpExpiresAt = min(now.plus(OTP_TTL), current.challengeExpiresAt());
        int nextVersion = current.version() + 1;
        String code = codeDifferentFromRecent(current);
        OtpStoreResult result = store.resend(current, codes.hmac(challengeId, nextVersion, code),
                codes.digest(challengeId, code), codes.keyId(), otpExpiresAt, now);
        switch (result.status()) {
            case OK -> {
                deliver(member, code, otpExpiresAt, challengeId, result.version(), DeliveryStage.RESEND);
                return new OtpResent(otpExpiresAt);
            }
            case INTERVAL_LIMITED, ISSUE_LIMITED, FAILURE_LIMITED -> throw limited(result.retryAfter());
            // 동시에 다른 재발송이 먼저 반영됨: 발급 간격 안의 요청과 같게 처리한다
            case VERSION_CONFLICT -> throw limited(issueInterval);
            default -> throw challengeInvalid();
        }
    }

    /** @return OTP를 확인한 회원 ID. Challenge는 소비됐다. */
    public long verify(String challengeId, String code) {
        OtpChallengeSnapshot current = store.find(challengeId).orElseThrow(OtpService::challengeInvalid);
        OtpStoreResult result = store.verify(current, codes.hmac(challengeId, current.version(), code),
                clock.instant());
        return switch (result.status()) {
            case OK -> result.memberId();
            case MISMATCH -> throw rejected(MemberErrorCode.OTP_MISMATCH, MemberErrorCode.OTP_MISMATCH.detail());
            case EXPIRED -> throw rejected(MemberErrorCode.OTP_EXPIRED, MemberErrorCode.OTP_EXPIRED.detail());
            case EXHAUSTED -> throw rejected(MemberErrorCode.OTP_EXPIRED, MemberErrorCode.OTP_EXHAUSTED_DETAIL);
            case VERSION_CONFLICT -> throw rejected(MemberErrorCode.OTP_EXPIRED, MemberErrorCode.OTP_REPLACED_DETAIL);
            case FAILURE_LIMITED, INTERVAL_LIMITED, ISSUE_LIMITED -> throw limited(result.retryAfter());
            case INVALID_CHALLENGE -> throw challengeInvalid();
        };
    }

    private void deliver(Member member, String code, Instant expiresAt, String challengeId, int version,
                         DeliveryStage stage) {
        try {
            mail.send(member.getEmail(), code, expiresAt);
        } catch (RuntimeException ex) {
            boolean compensated = compensate(challengeId, version, stage);
            log.warn("OTP 메일 발송 실패: memberId={}, stage={}, compensated={}, cause={}", member.getId(), stage,
                    compensated, ex.getClass().getSimpleName());
            throw rejected(MemberErrorCode.OTP_DELIVERY_UNAVAILABLE, MemberErrorCode.OTP_DELIVERY_UNAVAILABLE.detail());
        }
        // SMTP가 메일을 받은 것이 확인된 뒤에만 OTP를 검증 가능하게 한다(MB-D016). 그 사이 Challenge가 바뀌었으면 쓸 수 없다.
        if (!store.confirmDelivered(challengeId, version)) {
            log.warn("OTP 발송 확인을 반영하지 못함(Challenge 변경): memberId={}, stage={}", member.getId(), stage);
            throw rejected(MemberErrorCode.OTP_DELIVERY_UNAVAILABLE, MemberErrorCode.OTP_DELIVERY_UNAVAILABLE.detail());
        }
    }

    /**
     * 보상 실패(저장소 장애)는 메일 발송 실패 응답을 바꾸지 않는다. 남는 상태: 로그인이면 쿠키 없는 Challenge(만료까지 쓸 수 없음),
     * 재발송이면 받지 못한 새 OTP(회원이 모르므로 쓸 수 없음).
     */
    private boolean compensate(String challengeId, int version, DeliveryStage stage) {
        try {
            return store.deliveryFailed(challengeId, version, stage);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** 최근에 보냈거나 보내려 한 숫자(최대 2개)와 다른 4자리를 고른다. 실패한 메일도 전달됐을 수 있어 함께 피한다. */
    private String codeDifferentFromRecent(OtpChallengeSnapshot current) {
        for (int draw = 0; draw < MAX_CODE_DRAWS; draw++) {
            String code = codes.newCode();
            if (!current.recentDigests().contains(codes.digest(current.challengeId(), code))) {
                return code;
            }
        }
        throw new IllegalStateException("직전 OTP와 다른 값을 만들 수 없다");
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static TooManyRequestsException limited(Duration retryAfter) {
        MemberErrorCode code = MemberErrorCode.OTP_RATE_LIMITED;
        return new TooManyRequestsException(code, code.detail(), retryAfter);
    }

    private static BusinessException challengeInvalid() {
        return rejected(MemberErrorCode.OTP_CHALLENGE_INVALID, MemberErrorCode.OTP_CHALLENGE_INVALID.detail());
    }

    private static BusinessException rejected(MemberErrorCode code, String detail) {
        return new BusinessException(code, detail);
    }
}
