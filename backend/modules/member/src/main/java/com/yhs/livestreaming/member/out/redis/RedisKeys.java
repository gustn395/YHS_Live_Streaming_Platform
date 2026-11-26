package com.yhs.livestreaming.member.out.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Redis 키 이름. username·IP는 원문 대신 SHA-256 hex를 키에 쓴다. 원문이 키 목록에 그대로 남지 않게 하려는 것이며,
 * 입력 공간이 작아 사전 대입으로 되돌릴 수 있으므로 익명화로 보지 않는다.
 */
final class RedisKeys {

    static final String OTP_CHALLENGE_PREFIX = "auth:otp:challenge:";

    private RedisKeys() {
    }

    static String loginFailure(String username) {
        return "auth:login:fail:" + sha256(username);
    }

    static String loginBlock(String username) {
        return "auth:login:block:" + sha256(username);
    }

    static String rateLimit(String scope, String clientIp) {
        return "ratelimit:" + scope + ":" + sha256(clientIp);
    }

    static String otpChallenge(String challengeId) {
        return OTP_CHALLENGE_PREFIX + challengeId;
    }

    static String otpMember(long memberId, String suffix) {
        return "auth:otp:member:" + memberId + ":" + suffix;
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256을 쓸 수 없다", ex);
        }
    }
}
