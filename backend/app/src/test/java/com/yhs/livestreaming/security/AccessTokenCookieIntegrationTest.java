package com.yhs.livestreaming.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.global.security.jwt.AccessTokenAuthenticationFilter;
import com.yhs.livestreaming.global.security.jwt.AccessTokenIssuer;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import testsupport.AppIntegrationTest;
import testsupport.SecurityTestEndpoints;

/**
 * GL-T013(통합): 실제 보안 체인에서 Access 쿠키 → JWT 검증 → Redis 로그인 세션 확인 → {@code @CurrentMember}.
 * 필터가 보안 체인에 한 번만 있고 서블릿 필터로 따로 등록되지 않는지도 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
@Import(SecurityTestEndpoints.class)
class AccessTokenCookieIntegrationTest {

    private static final String SID = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @Autowired
    private LoginSessionStore sessions;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ApplicationContext context;

    @AfterEach
    void clean() {
        redis.delete(redis.keys("auth:*"));
    }

    @Test
    void accessCookieWithLiveSessionAuthenticatesMember() throws Exception {
        Instant expiresAt = Instant.now().plus(Duration.ofDays(7));
        sessions.create(new NewLoginSession(SID, 42L, "b".repeat(64), expiresAt));
        String token = issuer.issue(42L, "USER", SID, expiresAt).value();

        mockMvc.perform(get("/test/protected/me").cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, token)))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void validTokenWithoutLiveSessionIsUnauthenticated() throws Exception {
        String token = issuer.issue(42L, "USER", SID, Instant.now().plus(Duration.ofDays(7))).value();

        mockMvc.perform(get("/test/protected/me").cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void sessionOwnedByAnotherMemberIsUnauthenticated() throws Exception {
        Instant expiresAt = Instant.now().plus(Duration.ofDays(7));
        sessions.create(new NewLoginSession(SID, 7L, "c".repeat(64), expiresAt));
        String token = issuer.issue(42L, "USER", SID, expiresAt).value();

        mockMvc.perform(get("/test/protected/me").cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageAccessCookieDoesNotBlockPublicApis() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf").cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, "expired.or.forged")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/test/protected/me").cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, "expired.or.forged")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void filterIsRegisteredOnceInsideSecurityChainOnly() {
        FilterChainProxy proxy = context.getBean(FilterChainProxy.class);
        long inChain = proxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(AccessTokenAuthenticationFilter.class::isInstance)
                .count();
        assertThat(inChain).isEqualTo(1);
        assertThat(context.getBeansOfType(AccessTokenAuthenticationFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(FilterRegistrationBean.class).values())
                .noneMatch(registration -> registration.getFilter() instanceof AccessTokenAuthenticationFilter);
        assertThat(context.getBeansOfType(Filter.class).values())
                .noneMatch(AccessTokenAuthenticationFilter.class::isInstance);
    }
}
