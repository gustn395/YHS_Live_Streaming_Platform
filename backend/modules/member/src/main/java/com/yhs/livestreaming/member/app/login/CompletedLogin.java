package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.global.security.jwt.IssuedAccessToken;
import java.time.Instant;

/**
 * OTP 최종 인증 성공 결과(MB-D012~D014). 토큰 원문은 쿠키로만 내보내고 로그에 남기지 않는다.
 *
 * @param memberId         회원 ID
 * @param role             회원 역할
 * @param accessToken      Access Token(JWT)
 * @param refreshToken     Refresh Token 원문(저장소에는 SHA-256만 있다)
 * @param sessionExpiresAt 로그인 세션·Refresh Token 절대 만료
 */
public record CompletedLogin(long memberId, String role, IssuedAccessToken accessToken, String refreshToken,
                             Instant sessionExpiresAt) {

    @Override
    public String toString() {
        return "CompletedLogin[memberId=" + memberId + ", role=" + role + ", accessToken=****, refreshToken=****, "
                + "sessionExpiresAt=" + sessionExpiresAt + "]";
    }
}
