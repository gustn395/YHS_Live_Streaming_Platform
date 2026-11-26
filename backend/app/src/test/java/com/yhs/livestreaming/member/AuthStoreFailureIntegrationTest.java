package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.otp.NewOtpChallenge;
import com.yhs.livestreaming.member.app.otp.OtpChallengeSnapshot;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStore;
import com.yhs.livestreaming.member.app.otp.OtpStoreResult;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import com.yhs.livestreaming.member.in.MemberAuthCookies;
import com.yhs.livestreaming.member.out.redis.RedisLoginSessionStore;
import com.yhs.livestreaming.member.out.redis.RedisOtpChallengeStore;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import testsupport.AppIntegrationTest;
import testsupport.CapturingOtpMailSender;
import testsupport.RealCsrf;

/**
 * MB-T029(부분 장애): IP 요청 제한은 정상(실제 Redis)인 상태에서 OTP 조회 저장소나 로그인 세션 생성만 실패시킨다.
 * 각 요청은 503이고 인증 쿠키를 발급하지 않는다(MB-D014, MB-D019). Access GET의 세션 검증 장애는
 * {@code RedisUnavailableIntegrationTest}가 따로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
@Import(AuthStoreFailureIntegrationTest.FailingStores.class)
class AuthStoreFailureIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String EMAIL = "tester@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FailSwitch failSwitch;

    @Autowired
    private CapturingOtpMailSender mail;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        csrfToken = RealCsrf.issue(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        failSwitch.otpLookup = false;
        failSwitch.sessionCreate = false;
        failSwitch.sessionCreateAttempts.set(0);
        mail.reset();
        jdbc.update("DELETE FROM member_oauth_accounts");
        jdbc.update("DELETE FROM members");
        redis.delete(redis.keys("*"));
    }

    @Test
    void otpLookupFailureIsServiceUnavailableWithoutAuthCookies() throws Exception {
        Cookie challenge = signupAndLogin();
        failSwitch.otpLookup = true;

        MockHttpServletResponse response = mockMvc.perform(verify(challenge, mail.lastCodeFor(EMAIL)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"))
                .andReturn().getResponse();

        assertNoAuthCookies(response);
        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void sessionCreationFailureIsServiceUnavailableWithoutAuthCookies() throws Exception {
        Cookie challenge = signupAndLogin();
        failSwitch.sessionCreate = true;

        MockHttpServletResponse response = mockMvc.perform(verify(challenge, mail.lastCodeFor(EMAIL)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"))
                .andReturn().getResponse();

        assertNoAuthCookies(response);
        assertThat(failSwitch.sessionCreateAttempts.get()).isPositive(); // 이 테스트에서 세션 생성 단계까지 갔다
        assertThat(redis.keys("auth:session:*")).isEmpty();
        assertThat(redis.keys("auth:refresh:*")).isEmpty();
        // OTP로 이메일 소유가 확인된 뒤라 활성화는 남는다(설계: 다시 로그인하면 된다)
        assertThat(jdbc.queryForObject("SELECT account_status FROM members", String.class)).isEqualTo("ACTIVE");
    }

    private Cookie signupAndLogin() throws Exception {
        String body = """
                {"username":"tester01","password":"%s","passwordConfirm":"%s","email":"%s","nickname":"테스터"}
                """.formatted(PASSWORD, PASSWORD, EMAIL);
        mockMvc.perform(post("/api/v1/auth/signup").with(RealCsrf.token(csrfToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MockHttpServletResponse login = mockMvc.perform(post("/api/v1/auth/login").with(RealCsrf.token(csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester01\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie cookie = login.getCookie(MemberAuthCookies.OTP_CHALLENGE);
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private MockHttpServletRequestBuilder verify(Cookie challenge, String code) {
        return post("/api/v1/auth/otp/verify").with(RealCsrf.token(csrfToken)).cookie(challenge)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}");
    }

    private static void assertNoAuthCookies(MockHttpServletResponse response) throws Exception {
        assertThat(response.getHeaders("Set-Cookie"))
                .noneMatch(value -> value.startsWith(AuthCookieNames.ACCESS_TOKEN + "="))
                .noneMatch(value -> value.startsWith(MemberAuthCookies.REFRESH_TOKEN + "="));
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("memberId");
    }

    /** 테스트가 켜고 끄는 장애 스위치. */
    static class FailSwitch {
        volatile boolean otpLookup;
        volatile boolean sessionCreate;
        final java.util.concurrent.atomic.AtomicInteger sessionCreateAttempts = new java.util.concurrent.atomic.AtomicInteger();
    }

    /** 실제 Redis 저장소를 감싸 특정 호출만 실패시킨다(나머지는 그대로 위임). */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingStores {

        @Bean
        FailSwitch failSwitch() {
            return new FailSwitch();
        }

        @Bean
        @Primary
        OtpChallengeStore failingOtpChallengeStore(RedisOtpChallengeStore real, FailSwitch failSwitch) {
            return new OtpChallengeStore() {
                @Override
                public OtpStoreResult issueForLogin(NewOtpChallenge challenge, Instant now) {
                    return real.issueForLogin(challenge, now);
                }

                @Override
                public Optional<OtpChallengeSnapshot> find(String challengeId) {
                    if (failSwitch.otpLookup) {
                        throw new LoginSessionStoreUnavailableException("injected otp lookup failure", null);
                    }
                    return real.find(challengeId);
                }

                @Override
                public OtpStoreResult resend(OtpChallengeSnapshot current, String newOtpHmac, String newCodeDigest,
                                             String keyId, Instant otpExpiresAt, Instant now) {
                    return real.resend(current, newOtpHmac, newCodeDigest, keyId, otpExpiresAt, now);
                }

                @Override
                public OtpStoreResult verify(OtpChallengeSnapshot current, String candidateHmac, Instant now) {
                    return real.verify(current, candidateHmac, now);
                }

                @Override
                public boolean deliveryFailed(String challengeId, int version, DeliveryStage stage) {
                    return real.deliveryFailed(challengeId, version, stage);
                }

                @Override
                public boolean confirmDelivered(String challengeId, int version) {
                    return real.confirmDelivered(challengeId, version);
                }
            };
        }

        @Bean
        @Primary
        LoginSessionStore failingLoginSessionStore(RedisLoginSessionStore real, FailSwitch failSwitch) {
            return (NewLoginSession session) -> {
                if (failSwitch.sessionCreate) {
                    failSwitch.sessionCreateAttempts.incrementAndGet();
                    throw new LoginSessionStoreUnavailableException("injected session create failure", null);
                }
                return real.create(session);
            };
        }
    }
}
