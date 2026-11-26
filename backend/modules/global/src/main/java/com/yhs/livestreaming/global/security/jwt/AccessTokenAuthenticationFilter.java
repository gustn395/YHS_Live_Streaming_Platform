package com.yhs.livestreaming.global.security.jwt;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.auth.LoginSessionStatus;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.auth.LoginSessionVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Access 쿠키({@code __Host-yhs_at}) 인증 필터(G-D004, G-D006, G-D007, MB-D012).
 * <ul>
 *     <li>Access 쿠키만 읽는다. {@code Authorization} 헤더는 쓰지 않는다.</li>
 *     <li>쿠키가 없거나 토큰이 잘못·만료되면 익명으로 계속한다. 공개 API는 그대로 동작하고, 보호 API는 뒤에서 401이 된다.</li>
 *     <li>토큰이 유효하면 로그인 세션 허용 목록을 확인한다. {@code ACTIVE}일 때만 인증 주체를 넣는다.</li>
 *     <li>세션 저장소 오류는 통과시키지 않고 MVC 예외 처리기로 넘겨 503 ProblemDetail로 응답한 뒤 체인을 멈춘다.</li>
 * </ul>
 * 보안 체인에서 {@code new}로 한 번만 등록한다. 서블릿 필터로 따로 등록되지 않도록 Bean으로 만들지 않는다.
 * 토큰·세션 ID는 로그에 남기지 않는다.
 */
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AccessTokenVerifier tokens;
    private final LoginSessionVerifier sessions;
    private final HandlerExceptionResolver exceptionResolver;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AccessTokenAuthenticationFilter(AccessTokenVerifier tokens, LoginSessionVerifier sessions,
                                           HandlerExceptionResolver exceptionResolver) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<AccessTokenClaims> claims = accessToken(request).flatMap(tokens::verify);
        if (claims.isPresent()) {
            AccessTokenClaims verified = claims.get();
            LoginSessionStatus status;
            try {
                status = sessions.verify(verified.sessionId(), verified.memberId());
            } catch (LoginSessionStoreUnavailableException ex) {
                exceptionResolver.resolveException(request, response, null, ex);
                return;
            }
            if (status == LoginSessionStatus.ACTIVE) {
                authenticate(verified);
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(AccessTokenClaims claims) {
        AuthenticatedMember principal =
                new AuthenticatedMember(claims.memberId(), claims.role(), claims.sessionId());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
    }

    private static Optional<String> accessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieNames.ACCESS_TOKEN.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
