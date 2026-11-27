package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키 발급·삭제(MB-D013). 모든 쿠키는 HttpOnly·Secure·SameSite=Strict이고 Domain을 지정하지 않는다. 삭제는 발급과 같은
 * 이름·Path·속성에 Max-Age=0으로 한다. Max-Age는 만료 시각까지 남은 초를 올림한 값이다(절대 만료를 늘리지 않는다).
 */
@Component
public class AuthCookieWriter {

    private final Clock clock;

    public AuthCookieWriter(Clock clock) {
        this.clock = clock;
    }

    public void writeChallenge(HttpServletResponse response, String challengeId, Instant expiresAt) {
        add(response, MemberAuthCookies.OTP_CHALLENGE, challengeId, "/", remaining(expiresAt));
    }

    public void clearChallenge(HttpServletResponse response) {
        add(response, MemberAuthCookies.OTP_CHALLENGE, "", "/", Duration.ZERO);
    }

    public void writeAccessToken(HttpServletResponse response, String token, Instant expiresAt) {
        add(response, AuthCookieNames.ACCESS_TOKEN, token, "/", remaining(expiresAt));
    }

    public void writeRefreshToken(HttpServletResponse response, String token, Instant sessionExpiresAt) {
        add(response, MemberAuthCookies.REFRESH_TOKEN, token, MemberAuthCookies.REFRESH_TOKEN_PATH,
                remaining(sessionExpiresAt));
    }

    /** Access·Refresh 쿠키 삭제(Refresh 실패 401·403, MB-D020). */
    public void clearTokens(HttpServletResponse response) {
        add(response, AuthCookieNames.ACCESS_TOKEN, "", "/", Duration.ZERO);
        add(response, MemberAuthCookies.REFRESH_TOKEN, "", MemberAuthCookies.REFRESH_TOKEN_PATH, Duration.ZERO);
    }

    /** 로그아웃: Access·Refresh·OTP Challenge 쿠키 삭제(MB-D022). */
    public void clearAll(HttpServletResponse response) {
        clearTokens(response);
        clearChallenge(response);
    }

    private Duration remaining(Instant expiresAt) {
        long millis = Math.max(0, Duration.between(clock.instant(), expiresAt).toMillis());
        return Duration.ofSeconds((millis + 999) / 1000);
    }

    private static void add(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
