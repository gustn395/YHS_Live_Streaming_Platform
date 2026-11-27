package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.member.app.oauth.OAuthFailure;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Google 로그인 흐름의 고정 이동(MB-D024): 설정한 같은 출처 경로로만 302 한다. 목적지를 요청에서 받지 않는다. 이동 전에
 * SecurityContext를 비워 OAuth 인증 객체가 남지 않게 한다.
 */
final class OAuthRedirects {

    private final MemberOAuthProperties properties;

    OAuthRedirects(MemberOAuthProperties properties) {
        this.properties = properties;
    }

    void toOtp(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.sendRedirect(properties.otpRedirect());
    }

    void toFailure(HttpServletResponse response, OAuthFailure failure) throws IOException {
        SecurityContextHolder.clearContext();
        response.sendRedirect(properties.failureRedirect() + "?error=" + failure.code());
    }
}
