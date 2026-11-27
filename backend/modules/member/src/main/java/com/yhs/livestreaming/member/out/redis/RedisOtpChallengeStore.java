package com.yhs.livestreaming.member.out.redis;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties.OtpIssue;
import com.yhs.livestreaming.member.app.otp.NewOtpChallenge;
import com.yhs.livestreaming.member.app.otp.OtpChallengeSnapshot;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStore;
import com.yhs.livestreaming.member.app.otp.OtpStoreResult;
import com.yhs.livestreaming.member.app.otp.OtpStoreResult.Status;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis OTP Challenge 저장소(MB-D015~D017). 키 구조는 docs/member/module.md "Redis 키" 절.
 * 발급·재발송·검증·발송 실패 보상은 각각 Lua 스크립트 하나로 원자 처리한다. OTP 원문은 저장하지 않는다(HMAC만).
 */
@Component
public class RedisOtpChallengeStore implements OtpChallengeStore {

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> ISSUE = RedisLuaScripts.load("otp_issue", List.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> RESEND = RedisLuaScripts.load("otp_resend", List.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> VERIFY = RedisLuaScripts.load("otp_verify", List.class);
    private static final DefaultRedisScript<Long> DELIVERY_FAILED =
            RedisLuaScripts.load("otp_delivery_failed", Long.class);
    private static final DefaultRedisScript<Long> CONFIRM_DELIVERED =
            RedisLuaScripts.load("otp_confirm_delivered", Long.class);
    private static final String HOUR = Long.toString(Duration.ofHours(1).toMillis());
    private static final String DAY = Long.toString(Duration.ofHours(24).toMillis());

    private final StringRedisTemplate redis;
    private final OtpIssue limits;
    private final Clock clock;

    public RedisOtpChallengeStore(StringRedisTemplate redis, MemberLimitProperties limits, Clock clock) {
        this.redis = redis;
        this.limits = limits.otpIssue();
        this.clock = clock;
    }

    @Override
    public OtpStoreResult issueForLogin(NewOtpChallenge c, Instant now) {
        long member = c.memberId();
        List<?> result = run(() -> redis.execute(ISSUE,
                List.of(RedisKeys.otpMember(member, "current"), RedisKeys.otpMember(member, "last-issued"),
                        RedisKeys.otpMember(member, "issue:1h"), RedisKeys.otpMember(member, "issue:24h"),
                        RedisKeys.otpMember(member, "fail:24h"), RedisKeys.otpChallenge(c.challengeId())),
                c.challengeId(), Long.toString(member), c.otpHmac(), c.keyId(), millis(c.otpExpiresAt()),
                millis(c.challengeExpiresAt()), Long.toString(limits.interval().toMillis()),
                Integer.toString(limits.hourlyMax()), Integer.toString(limits.dailyMax()),
                Integer.toString(limits.dailyFailureMax()), HOUR, DAY, RedisKeys.OTP_CHALLENGE_PREFIX,
                c.codeDigest(), c.deliveryEmail()));
        long code = number(result, 0);
        long value = number(result, 1);
        if (code == 1) {
            return new OtpStoreResult(Status.OK, (int) value, member, Duration.ZERO);
        }
        if (code == -9) {
            // 새 challengeId 충돌(256비트 난수라 사실상 없음): 발급하지 않은 것으로 보고 호출자가 다시 만든다
            return OtpStoreResult.of(Status.INVALID_CHALLENGE);
        }
        return limited(code, value);
    }

    @Override
    public Optional<OtpChallengeSnapshot> find(String challengeId) {
        List<Object> values = run(() -> redis.opsForHash().multiGet(RedisKeys.otpChallenge(challengeId),
                List.of("memberId", "version", "keyId", "otpExpiresAt", "challengeExpiresAt", "sentDigest",
                        "prevSentDigest", "deliveryEmail")));
        // 수신 주소가 없는 Challenge는 이 필드가 생기기 전에 만든 것이다: 찾지 못한 것으로 보고 다시 로그인하게 한다
        if (values == null || values.get(0) == null || values.get(7) == null || ((String) values.get(7)).isEmpty()) {
            return Optional.empty();
        }
        long memberId = Long.parseLong((String) values.get(0));
        String current = run(() -> redis.opsForValue().get(RedisKeys.otpMember(memberId, "current")));
        Instant challengeExpiresAt = Instant.ofEpochMilli(Long.parseLong((String) values.get(4)));
        if (!challengeId.equals(current) || !challengeExpiresAt.isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(new OtpChallengeSnapshot(challengeId, memberId, Integer.parseInt((String) values.get(1)),
                (String) values.get(2), Instant.ofEpochMilli(Long.parseLong((String) values.get(3))),
                challengeExpiresAt, java.util.Arrays.asList((String) values.get(5), (String) values.get(6)),
                (String) values.get(7)));
    }

    @Override
    public OtpStoreResult resend(OtpChallengeSnapshot current, String newOtpHmac, String newCodeDigest, String keyId,
                                 Instant otpExpiresAt, Instant now) {
        long member = current.memberId();
        List<?> result = run(() -> redis.execute(RESEND,
                List.of(RedisKeys.otpChallenge(current.challengeId()), RedisKeys.otpMember(member, "current"),
                        RedisKeys.otpMember(member, "last-issued"), RedisKeys.otpMember(member, "issue:1h"),
                        RedisKeys.otpMember(member, "issue:24h"), RedisKeys.otpMember(member, "fail:24h")),
                current.challengeId(), Long.toString(member), Integer.toString(current.version()), newOtpHmac, keyId,
                millis(now), millis(otpExpiresAt), Long.toString(limits.interval().toMillis()),
                Integer.toString(limits.hourlyMax()), Integer.toString(limits.dailyMax()),
                Integer.toString(limits.dailyFailureMax()), HOUR, DAY, newCodeDigest));
        long code = number(result, 0);
        long value = number(result, 1);
        return switch ((int) code) {
            case 1 -> new OtpStoreResult(Status.OK, (int) value, member, Duration.ZERO);
            case 0 -> OtpStoreResult.of(Status.INVALID_CHALLENGE);
            case -4 -> OtpStoreResult.of(Status.VERSION_CONFLICT);
            default -> limited(code, value);
        };
    }

    @Override
    public OtpStoreResult verify(OtpChallengeSnapshot current, String candidateHmac, Instant now) {
        long member = current.memberId();
        List<?> result = run(() -> redis.execute(VERIFY,
                List.of(RedisKeys.otpChallenge(current.challengeId()), RedisKeys.otpMember(member, "current"),
                        RedisKeys.otpMember(member, "fail:24h")),
                current.challengeId(), Long.toString(member), Integer.toString(current.version()), candidateHmac,
                millis(now), Integer.toString(limits.attemptsPerOtp()), Integer.toString(limits.dailyFailureMax()),
                DAY));
        long code = number(result, 0);
        long value = number(result, 1);
        return switch ((int) code) {
            case 1 -> new OtpStoreResult(Status.OK, current.version(), value, Duration.ZERO);
            case 0 -> OtpStoreResult.of(Status.INVALID_CHALLENGE);
            case -3 -> new OtpStoreResult(Status.FAILURE_LIMITED, 0, 0, Duration.ofMillis(value));
            case -4 -> OtpStoreResult.of(Status.VERSION_CONFLICT);
            case -5 -> OtpStoreResult.of(Status.EXPIRED);
            case -6 -> OtpStoreResult.of(Status.MISMATCH);
            case -7 -> OtpStoreResult.of(Status.EXHAUSTED);
            default -> throw new LoginSessionStoreUnavailableException("OTP 검증 결과를 해석할 수 없다", null);
        };
    }

    @Override
    public boolean deliveryFailed(String challengeId, int version, DeliveryStage stage) {
        Long result = run(() -> {
            Object memberId = redis.opsForHash().get(RedisKeys.otpChallenge(challengeId), "memberId");
            if (memberId == null) {
                return 0L;
            }
            return redis.execute(DELIVERY_FAILED,
                    List.of(RedisKeys.otpChallenge(challengeId),
                            RedisKeys.otpMember(Long.parseLong((String) memberId), "current")),
                    challengeId, Integer.toString(version), stage.name());
        });
        return result != null && result == 1L;
    }

    @Override
    public boolean confirmDelivered(String challengeId, int version) {
        Long result = run(() -> redis.execute(CONFIRM_DELIVERED, List.of(RedisKeys.otpChallenge(challengeId)),
                Integer.toString(version)));
        return result != null && result == 1L;
    }

    private static OtpStoreResult limited(long code, long retryAfterMillis) {
        Status status = switch ((int) code) {
            case -1 -> Status.INTERVAL_LIMITED;
            case -2 -> Status.ISSUE_LIMITED;
            case -3 -> Status.FAILURE_LIMITED;
            default -> throw new LoginSessionStoreUnavailableException("OTP 발급 결과를 해석할 수 없다", null);
        };
        return new OtpStoreResult(status, 0, 0, Duration.ofMillis(retryAfterMillis));
    }

    private static long number(List<?> result, int index) {
        if (result == null || result.size() <= index) {
            throw new LoginSessionStoreUnavailableException("OTP 저장소 결과를 확인할 수 없다", null);
        }
        return ((Number) result.get(index)).longValue();
    }

    private static String millis(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }

    private static <T> T run(Supplier<T> command) {
        try {
            return command.get();
        } catch (DataAccessException ex) {
            throw new LoginSessionStoreUnavailableException("OTP 저장소를 사용할 수 없다", ex);
        }
    }
}
