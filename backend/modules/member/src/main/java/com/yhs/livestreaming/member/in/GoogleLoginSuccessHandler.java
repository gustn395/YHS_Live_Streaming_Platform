package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.TooManyRequestsException;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.oauth.GoogleIdentity;
import com.yhs.livestreaming.member.app.oauth.GoogleLoginService;
import com.yhs.livestreaming.member.app.oauth.OAuthFailure;
import com.yhs.livestreaming.member.app.oauth.OAuthLoginException;
import com.yhs.livestreaming.member.app.otp.LoginChallenge;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Google OIDC 성공 = 1차 인증 성공(MB-D023). 검증된 ID Token의 sub·email·email_verified·name만 꺼내 기존 이메일 OTP를 시작하고
 * OTP 입력 화면으로 보낸다. Access·Refresh 쿠키를 주지 않고 CSRF 토큰도 갱신하지 않는다. OAuth 인증 객체는 SecurityContext에서
 * 지운다. 임시 상태는 이미 소비됐으므로 결과와 관계없이 {@code __Host-yhs_oauth}를 지운다. 실패 사유는 고정 코드로만 알린다.
 */
class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginSuccessHandler.class);

    private final GoogleLoginService service;
    private final AuthCookieWriter cookies;
    private final OAuthRedirects redirects;

    GoogleLoginSuccessHandler(GoogleLoginService service, AuthCookieWriter cookies, OAuthRedirects redirects) {
        this.service = service;
        this.cookies = cookies;
        this.redirects = redirects;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        cookies.clearOAuthState(response);
        if (!(authentication.getPrincipal() instanceof OidcUser user)) {
            redirects.toFailure(response, OAuthFailure.OAUTH_FAILED);
            return;
        }
        GoogleIdentity identity = new GoogleIdentity(user.getSubject(), user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified()), user.getFullName());
        LoginChallenge challenge;
        try {
            challenge = service.login(identity);
        } catch (RuntimeException ex) {
            OAuthFailure failure = failureOf(ex);
            log.warn("Google 로그인 처리 실패: reason={}, cause={}", failure.code(), ex.getClass().getSimpleName());
            redirects.toFailure(response, failure);
            return;
        }
        cookies.writeChallenge(response, challenge.challengeId(), challenge.challengeExpiresAt());
        redirects.toOtp(response);
    }

    static OAuthFailure failureOf(RuntimeException ex) {
        if (ex instanceof OAuthLoginException rejected) {
            return rejected.failure();
        }
        if (ex instanceof TooManyRequestsException) {
            return OAuthFailure.RATE_LIMITED;
        }
        if (ex instanceof BusinessException business
                && business.errorCode() == MemberErrorCode.OTP_DELIVERY_UNAVAILABLE) {
            return OAuthFailure.OTP_DELIVERY_FAILED;
        }
        // 저장소(Redis·DB) 장애와 그 밖의 서버 오류는 확인할 수 없는 상태라 거부한다(fail-closed)
        return OAuthFailure.TEMPORARILY_UNAVAILABLE;
    }
}
