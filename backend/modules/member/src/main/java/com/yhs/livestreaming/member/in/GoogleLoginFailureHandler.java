package com.yhs.livestreaming.member.in;

import com.nimbusds.jose.KeySourceException;
import com.yhs.livestreaming.member.app.oauth.OAuthFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Google 콜백 실패(MB-D024). Google의 거절·state 불일치·토큰·ID Token 검증 실패는 {@code oauth_failed}, 실제 통신·저장소 장애는
 * {@code temporarily_unavailable}이다. 이번 요청이 임시 상태를 소비했을 때만 {@code __Host-yhs_oauth}를 지운다(이전 요청의 늦은
 * 콜백이면 최신 쿠키를 보존). 원인 예외 원문·인증 코드·state는 로그에 남기지 않는다(예외 종류와 OAuth 오류 코드만).
 */
class GoogleLoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginFailureHandler.class);
    private static final Set<String> UNAVAILABLE_CODES = Set.of(
            OAuthCookieAuthorizationRequestRepository.STORE_UNAVAILABLE, "server_error");
    private static final Pattern LOGGABLE_CODE = Pattern.compile("[a-z_]{1,64}");

    private final AuthCookieWriter cookies;
    private final OAuthRedirects redirects;

    GoogleLoginFailureHandler(AuthCookieWriter cookies, OAuthRedirects redirects) {
        this.cookies = cookies;
        this.redirects = redirects;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (Boolean.TRUE.equals(request.getAttribute(OAuthCookieAuthorizationRequestRepository.CONSUMED))) {
            cookies.clearOAuthState(response);
        }
        OAuthFailure failure = classify(exception);
        String error = exception instanceof OAuth2AuthenticationException oauth
                ? loggableCode(oauth.getError().getErrorCode()) : "unknown";
        log.warn("Google 로그인 실패: reason={}, error={}, cause={}", failure.code(), error,
                exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
        redirects.toFailure(response, failure);
    }

    /** 콜백 {@code ?error=}는 공격자가 정할 수 있다: OAuth 오류 코드 형식(소문자·밑줄, 64자 이하)만 그대로 남긴다. */
    static String loggableCode(String code) {
        return code != null && LOGGABLE_CODE.matcher(code).matches() ? code : "invalid";
    }

    static OAuthFailure classify(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth
                && UNAVAILABLE_CODES.contains(oauth.getError().getErrorCode())) {
            return OAuthFailure.TEMPORARILY_UNAVAILABLE;
        }
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException || cause instanceof ResourceAccessException
                    || cause instanceof HttpServerErrorException || cause instanceof KeySourceException) {
                return OAuthFailure.TEMPORARILY_UNAVAILABLE;
            }
        }
        return OAuthFailure.OAUTH_FAILED;
    }
}
