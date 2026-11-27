package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.TooManyRequestsException;
import com.yhs.livestreaming.member.app.limit.RateLimitScope;
import com.yhs.livestreaming.member.app.limit.RequestRateLimiter;
import com.yhs.livestreaming.member.app.oauth.OAuthFailure;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Google 로그인 IP 제한(MB-D024): 시작은 {@link RateLimitScope#OAUTH_START}로 임시 상태 저장 전에, 콜백은 LOCAL 1차 인증과 같은
 * {@link RateLimitScope#LOGIN}으로 Google Token Endpoint 호출 전에 센다. 초과·제한 저장소 장애는 실패 화면으로 보낸다(쿠키·임시
 * 상태는 건드리지 않는다). OAuth 보안 체인에서 {@code new}로 한 번만 등록한다(Bean이 아니므로 서블릿 필터로 따로 등록되지 않음).
 */
class OAuthRateLimitFilter extends OncePerRequestFilter {

    private final RequestRateLimiter limiter;
    private final OAuthRedirects redirects;

    OAuthRateLimitFilter(RequestRateLimiter limiter, OAuthRedirects redirects) {
        this.limiter = limiter;
        this.redirects = redirects;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitScope scope = MemberOAuthProperties.START_PATH.equals(request.getRequestURI())
                ? RateLimitScope.OAUTH_START : RateLimitScope.LOGIN;
        try {
            limiter.acquire(scope, ClientIp.of(request));
        } catch (TooManyRequestsException ex) {
            redirects.toFailure(response, OAuthFailure.RATE_LIMITED);
            return;
        } catch (BusinessException ex) {
            redirects.toFailure(response, OAuthFailure.TEMPORARILY_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }
}
