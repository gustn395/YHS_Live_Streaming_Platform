package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import com.yhs.livestreaming.member.app.session.RefreshRotation;
import com.yhs.livestreaming.member.app.session.RefreshTokenRecord;
import com.yhs.livestreaming.member.domain.Member;
import com.yhs.livestreaming.member.out.MemberRepositoryAdapter;
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
import org.springframework.dao.DataAccessResourceFailureException;
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
        failSwitch.refreshLookup = false;
        failSwitch.rotate = false;
        failSwitch.revoke = false;
        failSwitch.memberLookup = false;
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

    // ----- Refresh·로그아웃(MB-T039, MB-T041): 장애 시 아무것도 바꾸지 않고 쿠키·CSRF를 유지한다 -----

    @Test
    void refreshLookupFailureIsUnavailableAndKeepsCookies() throws Exception { // MB-T039
        Cookie[] auth = loggedInCookies();
        Object hashBefore = currentRefreshHash();
        failSwitch.refreshLookup = true;

        MockHttpServletResponse response = mockMvc.perform(refresh(auth))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"))
                .andReturn().getResponse();

        assertNoCookieChanges(response);
        assertThat(currentRefreshHash()).isEqualTo(hashBefore);
    }

    @Test
    void rotationFailureIsUnavailableAndKeepsCookiesAndSession() throws Exception { // MB-T039
        Cookie[] auth = loggedInCookies();
        Object hashBefore = currentRefreshHash();
        failSwitch.rotate = true;

        MockHttpServletResponse response = mockMvc.perform(refresh(auth))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"))
                .andReturn().getResponse();

        assertNoCookieChanges(response);
        assertThat(currentRefreshHash()).isEqualTo(hashBefore);
        failSwitch.rotate = false;
        mockMvc.perform(refresh(auth)).andExpect(status().isOk()); // 같은 Refresh로 다시 시도할 수 있다
    }

    @Test
    void revokeFailureAfterReuseDetectionIsUnavailableAndKeepsCookies() throws Exception { // MB-T039
        Cookie[] auth = loggedInCookies();
        mockMvc.perform(refresh(auth)).andExpect(status().isOk()); // 이제 auth의 Refresh는 USED
        failSwitch.revoke = true;

        MockHttpServletResponse response = mockMvc.perform(refresh(auth))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"))
                .andReturn().getResponse();

        assertNoCookieChanges(response);
    }

    @Test
    void databaseFailureDuringRefreshIsInternalErrorAndChangesNothing() throws Exception { // MB-T039
        Cookie[] auth = loggedInCookies();
        Object hashBefore = currentRefreshHash();
        failSwitch.memberLookup = true;

        MockHttpServletResponse response = mockMvc.perform(refresh(auth))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andReturn().getResponse();

        assertNoCookieChanges(response);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("injected");
        assertThat(currentRefreshHash()).isEqualTo(hashBefore);
        assertThat(redis.keys("auth:session:*")).hasSize(1);
        failSwitch.memberLookup = false;
        mockMvc.perform(refresh(auth)).andExpect(status().isOk());
    }

    @Test
    void databaseFailureDuringMyProfileKeepsSessionAndCookies() throws Exception { // MB-T056(MB-D028): 확인 불가는 폐기 사유가 아니다
        Cookie[] auth = loggedInCookies();
        failSwitch.memberLookup = true;

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/members/me").cookie(auth))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andReturn().getResponse();

        assertNoCookieChanges(response);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("injected");
        assertThat(redis.keys("auth:session:*")).hasSize(1);
        failSwitch.memberLookup = false;
        mockMvc.perform(get("/api/v1/members/me").cookie(auth)).andExpect(status().isOk());
    }

    @Test
    void logoutStoreFailureKeepsCookiesAndCsrfThenRetrySucceeds() throws Exception { // MB-T041
        Cookie[] auth = loggedInCookies();
        failSwitch.revoke = true;

        MockHttpServletResponse failed = mockMvc.perform(post("/api/v1/auth/logout").with(RealCsrf.token(csrfToken))
                        .cookie(auth))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"))
                .andReturn().getResponse();

        assertNoCookieChanges(failed);
        assertThat(redis.keys("auth:session:*")).hasSize(1);
        failSwitch.revoke = false;
        mockMvc.perform(post("/api/v1/auth/logout").with(RealCsrf.token(csrfToken)).cookie(auth))
                .andExpect(status().isNoContent());
        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    private Cookie[] loggedInCookies() throws Exception {
        Cookie challenge = signupAndLogin();
        MockHttpServletResponse verified = mockMvc.perform(verify(challenge, mail.lastCodeFor(EMAIL)))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie access = verified.getCookie(AuthCookieNames.ACCESS_TOKEN);
        Cookie refresh = verified.getCookie(MemberAuthCookies.REFRESH_TOKEN);
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        return new Cookie[] {new Cookie(access.getName(), access.getValue()),
                new Cookie(refresh.getName(), refresh.getValue())};
    }

    private MockHttpServletRequestBuilder refresh(Cookie[] auth) {
        return post("/api/v1/auth/refresh").with(RealCsrf.token(csrfToken)).cookie(auth[1]);
    }

    private Object currentRefreshHash() {
        String key = redis.keys("auth:session:*").iterator().next();
        return redis.opsForHash().get(key, "refreshHash");
    }

    private static void assertNoCookieChanges(MockHttpServletResponse response) {
        assertThat(response.getHeaders("Set-Cookie"))
                .noneMatch(value -> value.startsWith(AuthCookieNames.ACCESS_TOKEN + "="))
                .noneMatch(value -> value.startsWith(MemberAuthCookies.REFRESH_TOKEN + "="))
                .noneMatch(value -> value.startsWith(RealCsrf.COOKIE + "="));
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
        volatile boolean refreshLookup;
        volatile boolean rotate;
        volatile boolean revoke;
        volatile boolean memberLookup;
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
            return new LoginSessionStore() {
                @Override
                public CreateResult create(NewLoginSession session) {
                    if (failSwitch.sessionCreate) {
                        failSwitch.sessionCreateAttempts.incrementAndGet();
                        throw new LoginSessionStoreUnavailableException("injected session create failure", null);
                    }
                    return real.create(session);
                }

                @Override
                public Optional<RefreshTokenRecord> findRefresh(String refreshTokenHash) {
                    if (failSwitch.refreshLookup) {
                        throw new LoginSessionStoreUnavailableException("injected refresh lookup failure", null);
                    }
                    return real.findRefresh(refreshTokenHash);
                }

                @Override
                public RotateResult rotate(RefreshRotation rotation) {
                    if (failSwitch.rotate) {
                        throw new LoginSessionStoreUnavailableException("injected rotate failure", null);
                    }
                    return real.rotate(rotation);
                }

                @Override
                public RevokeResult revoke(String sessionId, long memberId) {
                    if (failSwitch.revoke) {
                        throw new LoginSessionStoreUnavailableException("injected revoke failure", null);
                    }
                    return real.revoke(sessionId, memberId);
                }
            };
        }

        /** 회원 조회(DB)만 실패시킨다. 나머지는 실제 JPA 어댑터에 맡긴다. */
        @Bean
        @Primary
        MemberRepository failingMemberRepository(MemberRepositoryAdapter real, FailSwitch failSwitch) {
            return new MemberRepository() {
                @Override
                public boolean existsByUsername(String username) {
                    return real.existsByUsername(username);
                }

                @Override
                public boolean existsLocalEmail(String email) {
                    return real.existsLocalEmail(email);
                }

                @Override
                public Member save(Member member) {
                    return real.save(member);
                }

                @Override
                public Optional<Member> findByUsername(String username) {
                    return real.findByUsername(username);
                }

                @Override
                public Optional<Member> findById(long id) {
                    if (failSwitch.memberLookup) {
                        throw new DataAccessResourceFailureException("injected db failure");
                    }
                    return real.findById(id);
                }

                @Override
                public int updatePasswordHash(long id, String expectedHash, String newHash, Instant now) {
                    return real.updatePasswordHash(id, expectedHash, newHash, now);
                }

                @Override
                public int activatePending(long id, Instant createdAfter, Instant now) {
                    return real.activatePending(id, createdAfter, now);
                }

                @Override
                public java.util.List<Member> findAllByIds(java.util.Collection<Long> ids) {
                    return real.findAllByIds(ids);
                }

                @Override
                public int updateNicknameOfActive(long id, String nickname, Instant now) {
                    return real.updateNicknameOfActive(id, nickname, now);
                }
            };
        }
    }
}
