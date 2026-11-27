package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.oauth.GoogleAccountRepository;
import com.yhs.livestreaming.member.app.oauth.OAuthStateStore;
import com.yhs.livestreaming.member.domain.Member;
import com.yhs.livestreaming.member.in.MemberAuthCookies;
import com.yhs.livestreaming.member.out.GoogleAccountRepositoryAdapter;
import com.yhs.livestreaming.member.out.redis.RedisOAuthStateStore;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import testsupport.AppIntegrationTest;
import testsupport.CapturingOtpMailSender;
import testsupport.FakeOidcProvider;
import testsupport.FakeOidcProvider.GoogleUser;
import testsupport.RealCsrf;
import testsupport.SecurityTestEndpoints;

/**
 * Google OIDC 로그인(MB-W11, Issue #21) 통합 테스트: 실제 Spring OAuth2 흐름(PKCE·nonce·ID Token 검증) + 가짜 OIDC 공급자 +
 * PostgreSQL·Redis(Testcontainers). 테스트 ID는 docs/member/test-spec.md MB-T044~MB-T053.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
@Import({SecurityTestEndpoints.class, GoogleOAuthLoginIntegrationTest.Faults.class})
@ExtendWith(OutputCaptureExtension.class)
class GoogleOAuthLoginIntegrationTest {

    private static final String START = "/api/v1/auth/oauth2/authorization/google";
    private static final String CALLBACK = "/api/v1/auth/oauth2/code/google";
    private static final String OAUTH_COOKIE = "__Host-yhs_oauth";
    private static final FakeOidcProvider GOOGLE = new FakeOidcProvider();
    private static final GoogleUser ALICE = new GoogleUser("google-sub-alice", "alice@example.com", true, "앨리스");

    @DynamicPropertySource
    static void oauth(DynamicPropertyRegistry registry) {
        registry.add("member.oauth.google.issuer-uri", GOOGLE::issuer);
        registry.add("member.oauth.google.authorization-uri", GOOGLE::authorizationUri);
        registry.add("member.oauth.google.token-uri", GOOGLE::tokenUri);
        registry.add("member.oauth.google.jwk-set-uri", GOOGLE::jwkSetUri);
        registry.add("member.limits.login.max", () -> "3");
        registry.add("member.limits.oauth-start.max", () -> "3");
        // 같은 회원이 테스트 안에서 여러 번 로그인한다: OTP 발급 간격만 줄이고 나머지는 정책 값
        registry.add("member.limits.otp-issue.interval", () -> "1ms");
        registry.add("member.limits.otp-issue.hourly-max", () -> "5");
        registry.add("member.limits.otp-issue.daily-max", () -> "20");
        registry.add("member.limits.otp-issue.daily-failure-max", () -> "10");
        registry.add("member.limits.otp-issue.attempts-per-otp", () -> "5");
    }

    @AfterAll
    static void stopProvider() {
        GOOGLE.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CapturingOtpMailSender mail;

    @Autowired
    private FaultSwitch faults;

    private String csrf;

    @BeforeEach
    void setUp() throws Exception {
        csrf = RealCsrf.issue(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        GOOGLE.reset();
        faults.reset();
        mail.reset();
        jdbc.update("DELETE FROM member_oauth_accounts");
        jdbc.update("DELETE FROM members");
        redis.delete(redis.keys("*"));
    }

    // ----- MB-T044 시작 -----

    @Test
    void startRedirectsToGoogleWithPkceNonceStateAndBindsTheBrowserWithALaxCookie() throws Exception {
        MvcResult result = mockMvc.perform(get(START).param("redirect", "https://evil.example/"))
                .andExpect(status().isFound()).andReturn();
        MockHttpServletResponse response = result.getResponse();

        String location = response.getRedirectedUrl();
        assertThat(location).startsWith(GOOGLE.authorizationUri()).doesNotContain("evil.example");
        Map<String, String> query = FakeOidcProvider.query(java.net.URI.create(location).getRawQuery());
        assertThat(query).containsEntry("response_type", "code").containsEntry("client_id", FakeOidcProvider.CLIENT_ID)
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("redirect_uri", "https://app.test/api/v1/auth/oauth2/code/google");
        assertThat(query.get("scope").split(" ")).contains("openid", "email", "profile");
        assertThat(query.get("state")).isNotBlank();
        assertThat(query.get("nonce")).isNotBlank();
        assertThat(query.get("code_challenge")).isNotBlank();

        String cookie = setCookie(response, OAUTH_COOKIE);
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/")
                .contains("Max-Age=600").doesNotContain("Domain=");
        String handleKeys = String.join(",", redis.keys("auth:oauth:*"));
        assertThat(redis.keys("auth:oauth:*")).hasSize(1);
        assertThat(handleKeys).doesNotContain(response.getCookie(OAUTH_COOKIE).getValue()); // 키는 해시
        assertThat(redis.getExpire(redis.keys("auth:oauth:*").iterator().next())).isBetween(590L, 600L);
        assertNoAuthOrCsrfCookies(response);
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff"); // 공통 보안 헤더 유지
    }

    @Test
    void undefinedOAuthPathsAndMethodsAreNotOpened() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/authorization/github")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/oauth2/anything")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/oauth2/code/github")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(START).with(RealCsrf.token(csrf))).andExpect(status().isUnauthorized());
        assertThat(redis.keys("auth:oauth:*")).isEmpty();
    }

    // ----- MB-T045·MB-T046 콜백 → OTP → 로그인 완료 -----

    @Test
    void firstGoogleLoginCreatesPendingMemberAndOnlyStartsOtp() throws Exception {
        Browser browser = start();

        MvcResult result = callback(browser, ALICE);
        MockHttpServletResponse response = result.getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login/otp");
        assertThat(setCookie(response, OAUTH_COOKIE)).contains("Max-Age=0");
        assertThat(setCookie(response, MemberAuthCookies.OTP_CHALLENGE)).contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict");
        assertNoAuthOrCsrfCookies(response);
        Map<String, Object> member = jdbc.queryForMap("SELECT * FROM members");
        assertThat(member).containsEntry("account_type", "GOOGLE").containsEntry("account_status", "PENDING")
                .containsEntry("email_verified", false).containsEntry("email", "alice@example.com");
        assertThat(member.get("username")).isNull();
        assertThat(jdbc.queryForObject("SELECT provider_user_id FROM member_oauth_accounts", String.class))
                .isEqualTo("google-sub-alice");
        assertThat(mail.sent()).singleElement().satisfies(sent -> assertThat(sent.email()).isEqualTo("alice@example.com"));
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(redis.keys("auth:session:*")).isEmpty(); // 로그인 세션은 OTP 전에는 없다
        assertThat(redis.keys("auth:oauth:*")).isEmpty(); // 임시 상태 소비
    }

    @Test
    void otpAfterGoogleCompletesLoginAndActivatesTheMember() throws Exception {
        Browser browser = start();
        Cookie challenge = cookie(callback(browser, ALICE).getResponse(), MemberAuthCookies.OTP_CHALLENGE);

        MockHttpServletResponse verified = verifyOtp(challenge, mail.lastCodeFor("alice@example.com"));

        assertThat(verified.getStatus()).isEqualTo(200);
        Cookie access = cookie(verified, AuthCookieNames.ACCESS_TOKEN);
        assertThat(verified.getCookie(RealCsrf.COOKIE)).isNotNull(); // 최종 인증 때 CSRF 갱신
        assertThat(jdbc.queryForMap("SELECT account_status, email_verified FROM members"))
                .containsEntry("account_status", "ACTIVE").containsEntry("email_verified", true);
        mockMvc.perform(get("/test/protected/me").cookie(access)).andExpect(status().isOk());
    }

    @Test
    void googleSuccessAloneNeverAuthenticatesProtectedApis() throws Exception {
        Browser browser = start();
        MockHttpServletResponse response = callback(browser, ALICE).getResponse();

        // 콜백 응답이 준 어떤 쿠키로도 보호 API를 쓸 수 없다
        Cookie[] all = response.getCookies();
        mockMvc.perform(get("/test/protected/me").cookie(all)).andExpect(status().isUnauthorized());
    }

    @Test
    void activeGoogleMemberEmailChangesOnlyAfterOtpSuccess() throws Exception {
        loginFully(ALICE);
        GoogleUser moved = new GoogleUser(ALICE.sub(), "alice.new@example.com", true, "앨리스");

        Cookie challenge = cookie(callback(start(), moved).getResponse(), MemberAuthCookies.OTP_CHALLENGE);
        assertThat(mail.sent().get(mail.sent().size() - 1).email()).isEqualTo("alice.new@example.com");
        assertThat(verifyOtp(challenge, "0000".equals(mail.lastCodeFor("alice.new@example.com")) ? "1111" : "0000")
                .getStatus()).isEqualTo(401); // 틀린 OTP
        assertThat(jdbc.queryForObject("SELECT email FROM members", String.class)).isEqualTo("alice@example.com");

        assertThat(verifyOtp(challenge, mail.lastCodeFor("alice.new@example.com")).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT email FROM members", String.class)).isEqualTo("alice.new@example.com");
        assertThat(jdbc.queryForObject("SELECT email FROM member_oauth_accounts", String.class))
                .isEqualTo("alice.new@example.com");
    }

    @Test
    void googleLoginWhileAlreadyLoggedInIssuesANewSessionAndKeepsTheOldOne() throws Exception {
        Cookie access = loginFully(ALICE);

        Cookie challenge = cookie(callback(start(access), ALICE).getResponse(), MemberAuthCookies.OTP_CHALLENGE);
        assertThat(verifyOtp(challenge, mail.lastCodeFor("alice@example.com")).getStatus()).isEqualTo(200);

        assertThat(redis.keys("auth:session:*")).hasSize(2);
        mockMvc.perform(get("/test/protected/me").cookie(access)).andExpect(status().isOk());
    }

    @Test
    void sameEmailAsALocalMemberCreatesASeparateGoogleMember() throws Exception {
        jdbc.update("INSERT INTO members (username, password_hash, email, email_verified, nickname, role, account_type, "
                + "account_status, created_at, updated_at) VALUES ('alice', 'h', 'alice@example.com', true, '로컬', "
                + "'USER', 'LOCAL', 'ACTIVE', now(), now())");

        callback(start(), ALICE);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE email = 'alice@example.com'", Long.class))
                .isEqualTo(2L);
    }

    // ----- MB-T047·MB-T048 거부 -----

    @Test
    void unverifiedOrMissingGoogleEmailIsRejectedWithoutAMember() throws Exception {
        assertFailure(callback(start(), new GoogleUser("s1", "a@example.com", false, "n")), "oauth_email_unverified");
        assertFailure(callback(start(), new GoogleUser("s2", null, true, "n")), "oauth_email_unverified");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isZero();
    }

    @Test
    void disabledGoogleMemberIsRejectedWithoutOtp() throws Exception {
        callback(start(), ALICE);
        jdbc.update("UPDATE members SET account_status = 'DISABLED'");
        mail.reset();

        assertFailure(callback(start(), ALICE), "account_disabled");
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void expiredPendingGoogleMemberIsRecreatedAndItsOldChallengeCannotAuthenticateTheNewMember() throws Exception {
        Cookie oldChallenge = cookie(callback(start(), ALICE).getResponse(), MemberAuthCookies.OTP_CHALLENGE);
        String oldCode = mail.lastCodeFor("alice@example.com");
        long oldId = jdbc.queryForObject("SELECT id FROM members", Long.class);
        jdbc.update("UPDATE members SET created_at = now() - interval '25 hours'");

        callback(start(), ALICE);

        long newId = jdbc.queryForObject("SELECT id FROM members", Long.class);
        assertThat(newId).isNotEqualTo(oldId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isEqualTo(1L);
        assertThat(verifyOtp(oldChallenge, oldCode).getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT account_status FROM members", String.class)).isEqualTo("PENDING");
    }

    @Test
    void idTokensWithWrongIssuerAudienceOrNonceAreRejected() throws Exception { // IP 제한(3) 안에서 나눈다
        assertFailure(callback(start(), ALICE, c -> c.issuer("https://evil.example")), "oauth_failed");
        assertFailure(callback(start(), ALICE, c -> c.audience("someone-else")), "oauth_failed");
        assertFailure(callback(start(), ALICE, c -> c.claim("nonce", "forged")), "oauth_failed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isZero();
    }

    @Test
    void expiredOrForeignSignedIdTokensAreRejected() throws Exception {
        assertFailure(callback(start(), ALICE, c -> c.expirationTime(java.util.Date.from(Instant.now().minusSeconds(60)))),
                "oauth_failed");
        GOOGLE.signWithForeignKey(true);
        assertFailure(callback(start(), ALICE), "oauth_failed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isZero();
    }

    @Test
    void pkceVerifierIsSentAndCheckedByTheTokenEndpoint() throws Exception {
        Browser browser = start();
        // 저장된 code_verifier를 바꾸면 공급자가 PKCE 검증으로 거부한다(실제 verifier가 저장·복원·전송됨을 증명)
        String key = redis.keys("auth:oauth:*").iterator().next();
        redis.opsForHash().put(key, "at.code_verifier", "tampered-verifier-tampered-verifier-tampered");

        assertFailure(callback(browser, ALICE), "oauth_failed");
        assertThat(GOOGLE.tokenCalls()).isEqualTo(1);
    }

    @Test
    void userCancellingAtGoogleConsumesTheStateAndClearsTheCookie() throws Exception {
        Browser browser = start();

        MvcResult result = mockMvc.perform(get(CALLBACK).param("error", "access_denied").param("state", browser.state())
                .cookie(browser.oauthCookie())).andReturn();

        assertFailure(result, "oauth_failed");
        assertThat(setCookie(result.getResponse(), OAUTH_COOKIE)).contains("Max-Age=0");
        assertThat(redis.keys("auth:oauth:*")).isEmpty();
    }

    // ----- MB-T049 쿠키 처리 표 -----

    @Test
    void callbackWithoutTheBrowserCookieFails() throws Exception {
        Browser browser = start();
        String query = GOOGLE.authorize(browser.location(), ALICE);

        assertFailure(mockMvc.perform(callbackRequest(query)).andReturn(), "oauth_failed");
        assertThat(GOOGLE.tokenCalls()).isZero();
    }

    @Test
    void lateCallbackOfAnOlderStartFailsAndKeepsTheLatestStateAndCookie() throws Exception {
        Browser first = start();
        Browser second = start();
        String query = GOOGLE.authorize(first.location(), ALICE);

        MvcResult late = mockMvc.perform(callbackRequest(query).cookie(second.oauthCookie())).andReturn();

        assertFailure(late, "oauth_failed");
        assertThat(late.getResponse().getHeaders("Set-Cookie")).noneMatch(v -> v.startsWith(OAUTH_COOKIE + "="));
        assertThat(GOOGLE.tokenCalls()).isZero();
        // 최신 시작은 그대로 완료할 수 있다
        assertThat(callback(second, ALICE).getResponse().getRedirectedUrl()).isEqualTo("/login/otp");
    }

    @Test
    void reusingAConsumedStateFails() throws Exception {
        Browser browser = start();
        String query = GOOGLE.authorize(browser.location(), ALICE);
        mockMvc.perform(callbackRequest(query).cookie(browser.oauthCookie())).andExpect(status().isFound());

        assertFailure(mockMvc.perform(callbackRequest(query).cookie(browser.oauthCookie())).andReturn(),
                "oauth_failed");
        assertThat(GOOGLE.tokenCalls()).isEqualTo(1);
    }

    @Test
    void concurrentCallbacksWithTheSameStateExchangeTheCodeAtMostOnce() throws Exception {
        Browser browser = start();
        String query = GOOGLE.authorize(browser.location(), ALICE);
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    return mockMvc.perform(callbackRequest(query).cookie(browser.oauthCookie())).andReturn()
                            .getResponse().getRedirectedUrl();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<String> targets = new ArrayList<>();
            for (Future<String> future : futures) {
                targets.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(targets).filteredOn("/login/otp"::equals).hasSize(1);
            assertThat(GOOGLE.tokenCalls()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void delayedChallengeCookieFromAnOlderLoginNeverAuthenticatesTheWrongMember() throws Exception {
        // 두 탭에서 서로 다른 Google 계정으로 콜백: 브라우저에 남는 Challenge가 무엇이든 그 Challenge의 회원으로만 끝난다
        GoogleUser bob = new GoogleUser("google-sub-bob", "bob@example.com", true, "밥");
        Cookie aliceChallenge = cookie(callback(start(), ALICE).getResponse(), MemberAuthCookies.OTP_CHALLENGE);
        Cookie bobChallenge = cookie(callback(start(), bob).getResponse(), MemberAuthCookies.OTP_CHALLENGE);

        assertThat(verifyOtp(aliceChallenge, mail.lastCodeFor("bob@example.com")).getStatus()).isEqualTo(401);
        MockHttpServletResponse bobDone = verifyOtp(bobChallenge, mail.lastCodeFor("bob@example.com"));
        assertThat(bobDone.getStatus()).isEqualTo(200);
        long bobId = jdbc.queryForObject("SELECT id FROM members WHERE email = 'bob@example.com'", Long.class);
        assertThat(bobDone.getContentAsString(StandardCharsets.UTF_8)).contains("\"memberId\":" + bobId);
    }

    // ----- MB-T050 Rate Limit·장애 -----

    @Test
    void startIsLimitedPerIpBeforeStoringState() throws Exception {
        for (int i = 0; i < 3; i++) {
            start();
        }
        assertThat(redis.keys("ratelimit:oauth-start:*")).singleElement()
                .satisfies(key -> assertThat(redis.opsForValue().get(key)).isEqualTo("3")); // 요청당 1번만 센다

        assertFailure(mockMvc.perform(get(START)).andReturn(), "rate_limited");
        assertThat(redis.keys("auth:oauth:*")).hasSize(3);
    }

    @Test
    void callbackSharesTheLoginLimitWithLocalLoginAndStopsBeforeTheTokenEndpoint() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login").with(RealCsrf.token(csrf)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nobody\",\"password\":\"correct horse battery staple\"}"))
                    .andExpect(status().isUnauthorized());
        }
        callback(start(), ALICE); // 3번째(로그인 범위 공유)
        int tokenCallsBefore = GOOGLE.tokenCalls();

        assertFailure(callback(start(), ALICE), "rate_limited");
        assertThat(GOOGLE.tokenCalls()).isEqualTo(tokenCallsBefore);
    }

    @Test
    void googleTokenEndpointFailureIsTemporarilyUnavailable() throws Exception {
        GOOGLE.tokenStatus(500);
        assertFailure(callback(start(), ALICE), "temporarily_unavailable");
    }

    @Test
    void googleKeysUnavailableIsTemporarilyUnavailable() throws Exception {
        GOOGLE.jwksAvailable(false);
        GOOGLE.rotateKey(true); // 캐시에 없는 kid → JWKS를 다시 가져와야 한다
        assertFailure(callback(start(), ALICE), "temporarily_unavailable");
    }

    @Test
    void stateStoreFailureAtStartAndCallbackIsTemporarilyUnavailable() throws Exception {
        faults.stateStore = true;
        MvcResult failedStart = mockMvc.perform(get(START)).andReturn();
        assertFailure(failedStart, "temporarily_unavailable");
        assertThat(failedStart.getResponse().getHeaders("Set-Cookie")).noneMatch(v -> v.startsWith(OAUTH_COOKIE + "="));

        faults.stateStore = false;
        Browser browser = start();
        faults.stateStore = true;
        assertFailure(callback(browser, ALICE), "temporarily_unavailable");
        assertThat(GOOGLE.tokenCalls()).isZero();
    }

    @Test
    void databaseFailureIsTemporarilyUnavailable() throws Exception {
        faults.database = true;
        assertFailure(callback(start(), ALICE), "temporarily_unavailable");
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void smtpFailureKeepsTheSavedPendingMemberForTheNextLogin() throws Exception {
        mail.failing(true);
        assertFailure(callback(start(), ALICE), "otp_delivery_failed");
        long id = jdbc.queryForObject("SELECT id FROM members", Long.class);

        mail.failing(false);
        callback(start(), ALICE);
        assertThat(jdbc.queryForObject("SELECT id FROM members", Long.class)).isEqualTo(id);
    }

    // ----- MB-T051 비노출 -----

    @Test
    void googleTokensCodesAndIdentityNeverAppearInLogsOrSessions(CapturedOutput output) throws Exception {
        Browser browser = start();
        String query = GOOGLE.authorize(browser.location(), ALICE);
        MvcResult result = mockMvc.perform(callbackRequest(query).cookie(browser.oauthCookie())).andReturn();
        String code = FakeOidcProvider.query(query).get("code");

        assertThat(result.getRequest().getSession(false)).isNull();
        for (String secret : Set.of(code, browser.state(), browser.nonce(), ALICE.sub(), ALICE.email(),
                browser.oauthCookie().getValue(), "fake-access-")) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEmpty();
    }

    // ----- 도우미 -----

    record Browser(String location, Cookie oauthCookie) {
        String state() {
            return FakeOidcProvider.query(java.net.URI.create(location).getRawQuery()).get("state");
        }

        String nonce() {
            return FakeOidcProvider.query(java.net.URI.create(location).getRawQuery()).get("nonce");
        }
    }

    private Browser start(Cookie... extra) throws Exception {
        var request = get(START);
        if (extra.length > 0) {
            request.cookie(extra);
        }
        MockHttpServletResponse response = mockMvc.perform(request).andExpect(status().isFound()).andReturn()
                .getResponse();
        return new Browser(response.getRedirectedUrl(), cookie(response, OAUTH_COOKIE));
    }

    private MvcResult callback(Browser browser, GoogleUser user) throws Exception {
        return callback(browser, user, c -> { });
    }

    private MvcResult callback(Browser browser, GoogleUser user,
                               java.util.function.Consumer<com.nimbusds.jwt.JWTClaimsSet.Builder> mutator)
            throws Exception {
        String query = GOOGLE.authorize(browser.location(), user, mutator);
        return mockMvc.perform(callbackRequest(query).cookie(browser.oauthCookie())).andReturn();
    }

    /** 콜백 쿼리를 요청 파라미터로 붙인다(문자열 URL로 넘기면 MockMvc가 state의 %3D를 다시 인코딩한다). */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder callbackRequest(
            String query) {
        var request = get(CALLBACK);
        FakeOidcProvider.query(query).forEach(request::param);
        return request;
    }

    private MockHttpServletResponse verifyOtp(Cookie challenge, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/otp/verify").with(RealCsrf.token(csrf)).cookie(challenge)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}")).andReturn()
                .getResponse();
    }

    private Cookie loginFully(GoogleUser user) throws Exception {
        Cookie challenge = cookie(callback(start(), user).getResponse(), MemberAuthCookies.OTP_CHALLENGE);
        MockHttpServletResponse done = verifyOtp(challenge, mail.lastCodeFor(user.email()));
        assertThat(done.getStatus()).isEqualTo(200);
        return cookie(done, AuthCookieNames.ACCESS_TOKEN);
    }

    private static void assertFailure(MvcResult result, String code) {
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error=" + code);
        assertNoAuthOrCsrfCookies(result.getResponse());
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(v -> v.startsWith(MemberAuthCookies.OTP_CHALLENGE + "=") && !v.contains("Max-Age=0"));
    }

    private static void assertNoAuthOrCsrfCookies(MockHttpServletResponse response) {
        assertThat(response.getHeaders("Set-Cookie"))
                .noneMatch(v -> v.startsWith(AuthCookieNames.ACCESS_TOKEN + "="))
                .noneMatch(v -> v.startsWith(MemberAuthCookies.REFRESH_TOKEN + "="))
                .noneMatch(v -> v.startsWith(RealCsrf.COOKIE + "="));
    }

    private static Cookie cookie(MockHttpServletResponse response, String name) {
        Cookie cookie = response.getCookie(name);
        assertThat(cookie).as("쿠키 " + name).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private static String setCookie(MockHttpServletResponse response, String name) {
        return response.getHeaders("Set-Cookie").stream().filter(v -> v.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie 없음: " + name));
    }

    // ----- 장애 주입 -----

    static class FaultSwitch {
        volatile boolean stateStore;
        volatile boolean database;

        void reset() {
            stateStore = false;
            database = false;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Faults {

        @Bean
        FaultSwitch faultSwitch() {
            return new FaultSwitch();
        }

        @Bean
        @Primary
        OAuthStateStore failingOAuthStateStore(RedisOAuthStateStore real, FaultSwitch faults) {
            return new OAuthStateStore() {
                @Override
                public void save(String handle, Map<String, String> fields, Duration ttl) {
                    check();
                    real.save(handle, fields, ttl);
                }

                @Override
                public Optional<Map<String, String>> find(String handle, String expectedState) {
                    check();
                    return real.find(handle, expectedState);
                }

                @Override
                public Optional<Map<String, String>> consume(String handle, String expectedState) {
                    check();
                    return real.consume(handle, expectedState);
                }

                private void check() {
                    if (faults.stateStore) {
                        throw new LoginSessionStoreUnavailableException("injected oauth state failure", null);
                    }
                }
            };
        }

        @Bean
        @Primary
        GoogleAccountRepository failingGoogleAccounts(GoogleAccountRepositoryAdapter real, FaultSwitch faults) {
            return new GoogleAccountRepository() {
                @Override
                public Optional<Member> findBySub(String sub) {
                    if (faults.database) {
                        throw new DataAccessResourceFailureException("injected db failure");
                    }
                    return real.findBySub(sub);
                }

                @Override
                public Member create(Member newMember, String sub, String linkEmail) {
                    return real.create(newMember, sub, linkEmail);
                }

                @Override
                public Optional<Member> replaceExpiredPending(long id, String sub, Instant before, Member m, String e) {
                    return real.replaceExpiredPending(id, sub, before, m, e);
                }

                @Override
                public int activatePending(long id, String email, Instant createdAfter, Instant now) {
                    return real.activatePending(id, email, createdAfter, now);
                }

                @Override
                public int updateActiveEmail(long id, String email, Instant now) {
                    return real.updateActiveEmail(id, email, now);
                }
            };
        }
    }
}
