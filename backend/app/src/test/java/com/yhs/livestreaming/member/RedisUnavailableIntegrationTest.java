package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.member.in.MemberAuthCookies;
import com.yhs.livestreaming.global.security.jwt.AccessTokenIssuer;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import testsupport.CapturingOtpMailSenderConfiguration;
import testsupport.PostgresTestcontainersConfiguration;
import testsupport.SecurityTestEndpoints;
import testsupport.StubBreachedPasswordCheckerConfiguration;

/**
 * MB-T029: Redis를 쓸 수 없으면 가입·로그인·OTP·로그인 세션 검증을 503으로 거부하고 인증 쿠키를 주지 않는다(MB-D019, fail-closed).
 * Redis 컨테이너 없이 닿지 않는 주소로 띄운다.
 */
@SpringBootTest(properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.timeout=500ms",
        "spring.data.redis.connect-timeout=300ms"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfiguration.class, StubBreachedPasswordCheckerConfiguration.class,
        CapturingOtpMailSenderConfiguration.class, SecurityTestEndpoints.class})
class RedisUnavailableIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer issuer;

    @Test
    void signupIsRejectedWhenRateLimitStoreIsDown() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"tester01","password":"%s","passwordConfirm":"%s",
                                 "email":"tester@example.com","nickname":"테스터"}""".formatted(PASSWORD, PASSWORD)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMON_RATE_LIMIT_UNAVAILABLE"));
    }

    @Test
    void loginAndOtpAreRejectedWithoutCookies() throws Exception {
        MockHttpServletResponse login = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester01\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse();
        MockHttpServletResponse verify = mockMvc.perform(post("/api/v1/auth/otp/verify").with(csrf())
                        .cookie(new Cookie(MemberAuthCookies.OTP_CHALLENGE, "x".repeat(43)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"1234\"}"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse();

        assertThat(login.getHeaders("Set-Cookie")).noneMatch(value -> value.startsWith("__"));
        assertThat(verify.getHeaders("Set-Cookie")).noneMatch(value -> value.startsWith("__"));
    }

    @Test
    void validAccessTokenIsNotTrustedWhenSessionStoreIsDown() throws Exception {
        String sid = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";
        String token = issuer.issue(42L, "USER", sid, Instant.now().plus(Duration.ofDays(1))).value();

        mockMvc.perform(get("/test/protected/me").cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"));
    }
}
