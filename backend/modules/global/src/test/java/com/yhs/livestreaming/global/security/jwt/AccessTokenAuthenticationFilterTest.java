package com.yhs.livestreaming.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.auth.LoginSessionStatus;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.auth.LoginSessionVerifier;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * GL-T013: Access 쿠키 인증 필터(MB-D012, G-D007). Access 쿠키만 읽고, 잘못된 토큰은 익명으로 통과시키며(공개 API를 막지 않음),
 * 세션 저장소 오류는 체인을 멈추고 MVC 예외 처리기(ProblemDetail 503)로 넘긴다.
 */
class AccessTokenAuthenticationFilterTest {

    private static final String GOOD = "good-token";
    private static final String SID = JwtAccessTokenServiceTest.SESSION_ID;
    private static final AccessTokenClaims CLAIMS =
            new AccessTokenClaims(42L, "USER", SID, Instant.parse("2026-11-26T10:15:00Z"));

    private final List<String> sessionChecks = new ArrayList<>();
    private LoginSessionStatus sessionStatus = LoginSessionStatus.ACTIVE;
    private RuntimeException sessionFailure;
    private final AtomicReference<Exception> resolved = new AtomicReference<>();
    private final AtomicReference<Authentication> seenByChain = new AtomicReference<>();

    private final AccessTokenVerifier tokens = token -> GOOD.equals(token) ? Optional.of(CLAIMS) : Optional.empty();
    private final LoginSessionVerifier sessions = (sessionId, memberId) -> {
        sessionChecks.add(sessionId + ":" + memberId);
        if (sessionFailure != null) {
            throw sessionFailure;
        }
        return sessionStatus;
    };
    private final HandlerExceptionResolver resolver = (request, response, handler, ex) -> {
        resolved.set(ex);
        response.setStatus(503);
        return new ModelAndView();
    };
    private final AccessTokenAuthenticationFilter filter =
            new AccessTokenAuthenticationFilter(tokens, sessions, resolver);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validCookieWithActiveSessionAuthenticatesMember() throws Exception {
        MockFilterChain chain = run(request(GOOD));

        assertThat(chain.getRequest()).isNotNull();
        Authentication authentication = seenByChain.get();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedMember(42L, "USER", SID));
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(sessionChecks).containsExactly(SID + ":42");
    }

    @Test
    void noCookieContinuesAnonymously() throws Exception {
        MockFilterChain chain = run(new MockHttpServletRequest("GET", "/api/x"));

        assertThat(chain.getRequest()).isNotNull();
        assertThat(seenByChain.get()).isNull();
        assertThat(sessionChecks).isEmpty();
    }

    @Test
    void invalidOrExpiredTokenContinuesAnonymouslySoPublicApisStillWork() throws Exception {
        MockFilterChain chain = run(request("expired-or-forged"));

        assertThat(chain.getRequest()).isNotNull();
        assertThat(seenByChain.get()).isNull();
        assertThat(sessionChecks).isEmpty();
    }

    @Test
    void authorizationHeaderIsIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader("Authorization", "Bearer " + GOOD);

        run(request);

        assertThat(seenByChain.get()).isNull();
    }

    @Test
    void missingOrForeignSessionContinuesAnonymously() throws Exception {
        sessionStatus = LoginSessionStatus.NOT_FOUND;
        run(request(GOOD));
        assertThat(seenByChain.get()).isNull();

        sessionStatus = LoginSessionStatus.MEMBER_MISMATCH;
        run(request(GOOD));
        assertThat(seenByChain.get()).isNull();
    }

    @Test
    void sessionStoreFailureStopsChainAndDelegatesToProblemDetailHandler() throws Exception {
        sessionFailure = new LoginSessionStoreUnavailableException("redis down", new RuntimeException());

        MockFilterChain chain = run(request(GOOD));

        assertThat(chain.getRequest()).isNull();
        assertThat(resolved.get()).isInstanceOf(LoginSessionStoreUnavailableException.class);
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.setCookies(new Cookie(AuthCookieNames.ACCESS_TOKEN, token));
        return request;
    }

    private MockFilterChain run(MockHttpServletRequest request) throws Exception {
        seenByChain.set(null);
        SecurityContextHolder.clearContext();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                seenByChain.set(SecurityContextHolder.getContext().getAuthentication());
                super.doFilter(req, res);
            }
        };
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }
}
