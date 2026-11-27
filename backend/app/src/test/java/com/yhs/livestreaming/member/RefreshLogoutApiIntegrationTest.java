package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.global.security.jwt.AccessTokenIssuer;
import com.yhs.livestreaming.member.in.MemberAuthCookies;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import testsupport.AppIntegrationTest;
import testsupport.CapturingOtpMailSender;
import testsupport.RealCsrf;
import testsupport.SecurityTestEndpoints;

/**
 * Refresh Rotation·재사용 탐지·로그아웃(MB-W09·W10, Issue #17) 통합 테스트. 실제 보안 체인·CSRF·PostgreSQL·Redis를 쓴다.
 * 테스트 ID는 docs/member/test-spec.md(MB-T035~MB-T042). 저장소 단위 원자성·동시성은 member 모듈 Redis 테스트가 맡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
@Import(SecurityTestEndpoints.class)
@ExtendWith(OutputCaptureExtension.class)
class RefreshLogoutApiIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final long MAX_AGE_SLACK = 10;
    private static final long SEVEN_DAYS = 604800L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CapturingOtpMailSender mail;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    private String csrfToken;

    /** 로그인을 마친 브라우저 한 개의 쿠키 상태. */
    record Browser(long memberId, Cookie access, Cookie refresh, String csrf) {
    }

    @BeforeEach
    void issueCsrf() throws Exception {
        csrfToken = RealCsrf.issue(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        mail.reset();
        jdbc.update("DELETE FROM member_oauth_accounts");
        jdbc.update("DELETE FROM members");
        redis.delete(redis.keys("*"));
    }

    // ----- Refresh -----

    @Test
    void refreshWithOnlyTheRefreshCookieRotatesKeepsSessionAndKeepsCsrf() throws Exception { // MB-T035·MB-T040
        Browser browser = loggedIn("tester01");
        String sid = onlySessionId();

        MockHttpServletResponse response = mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(browser.memberId()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
                .andReturn().getResponse();

        assertThat(setCookie(response, AuthCookieNames.ACCESS_TOKEN)).contains("Path=/").contains("HttpOnly")
                .contains("Secure").contains("SameSite=Strict").doesNotContain("Domain=");
        assertThat(maxAge(response, AuthCookieNames.ACCESS_TOKEN)).isBetween(900L - MAX_AGE_SLACK, 900L);
        assertThat(setCookie(response, MemberAuthCookies.REFRESH_TOKEN)).contains("Path=/api/v1/auth")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict").doesNotContain("Domain=");
        // 남은 절대 만료 시간(연장 없음): 세션 키의 PEXPIRETIME 기준
        long remaining = (sessionExpiry(sid) - System.currentTimeMillis() + 999) / 1000;
        assertThat(maxAge(response, MemberAuthCookies.REFRESH_TOKEN)).isBetween(remaining - MAX_AGE_SLACK, remaining)
                .isLessThanOrEqualTo(SEVEN_DAYS);
        assertThat(response.getHeaders("Set-Cookie")).noneMatch(v -> v.startsWith(RealCsrf.COOKIE + "="));

        Cookie newRefresh = cookie(response, MemberAuthCookies.REFRESH_TOKEN);
        Cookie newAccess = cookie(response, AuthCookieNames.ACCESS_TOKEN);
        assertThat(newRefresh.getValue()).isNotEqualTo(browser.refresh().getValue());
        assertThat(onlySessionId()).isEqualTo(sid); // sid 유지
        assertThat(redis.opsForHash().get("auth:session:" + sid, "refreshHash"))
                .isEqualTo(sha256(newRefresh.getValue()));
        // 기존 Access·새 Access 모두 유효
        mockMvc.perform(get("/test/protected/me").cookie(browser.access())).andExpect(status().isOk());
        mockMvc.perform(get("/test/protected/me").cookie(newAccess)).andExpect(status().isOk());
        // 기존 CSRF 토큰으로 상태 변경 요청 가능
        mockMvc.perform(post("/test/public/echo").with(RealCsrf.token(browser.csrf()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ok\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void accessCookieNeverOutlivesASessionThatEndsWithinFifteenMinutes() throws Exception { // MB-T040, MB-D012
        Browser browser = loggedIn("tester01");
        String sid = onlySessionId();
        // 세션 절대 만료가 5분 남은 상태를 만든다(세션·Refresh 키의 저장값과 Redis 만료를 함께 당긴다)
        long soon = System.currentTimeMillis() + 300_000;
        String refreshKey = "auth:refresh:" + sha256(browser.refresh().getValue());
        redis.opsForHash().put("auth:session:" + sid, "expiresAt", Long.toString(soon));
        redis.opsForHash().put(refreshKey, "expiresAt", Long.toString(soon));
        redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "redis.call('PEXPIREAT', KEYS[1], ARGV[1]); return redis.call('PEXPIREAT', KEYS[2], ARGV[1])",
                Long.class), List.of("auth:session:" + sid, refreshKey), Long.toString(soon));

        MockHttpServletResponse response = mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();

        long remaining = (soon - System.currentTimeMillis() + 999) / 1000;
        assertThat(maxAge(response, AuthCookieNames.ACCESS_TOKEN)).isBetween(remaining - MAX_AGE_SLACK, 300L);
        assertThat(maxAge(response, MemberAuthCookies.REFRESH_TOKEN)).isBetween(remaining - MAX_AGE_SLACK, 300L);
        assertThat(Instant.parse(com.jayway.jsonpath.JsonPath.read(response.getContentAsString(StandardCharsets.UTF_8),
                "$.accessTokenExpiresAt"))).isBeforeOrEqualTo(Instant.ofEpochMilli(soon));
    }

    @Test
    void refreshWithAValidAccessCookieAlsoKeepsTheCsrfToken() throws Exception { // MB-T040
        Browser browser = loggedIn("tester01");

        MockHttpServletResponse response = mockMvc.perform(refresh(browser.refresh(), browser.csrf())
                        .cookie(browser.access()))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getHeaders("Set-Cookie")).noneMatch(v -> v.startsWith(RealCsrf.COOKIE + "="));
    }

    @Test
    void refreshWorksWithExpiredOrForgedAccessCookie() throws Exception { // MB-T040
        Browser browser = loggedIn("tester01");
        Cookie forged = new Cookie(AuthCookieNames.ACCESS_TOKEN, browser.access().getValue() + "x");
        Cookie expired = new Cookie(AuthCookieNames.ACCESS_TOKEN, accessTokenIssuer
                .issue(browser.memberId(), "USER", onlySessionId(), Instant.now().plusMillis(1100)).value());
        Thread.sleep(1500);
        mockMvc.perform(get("/test/protected/me").cookie(expired)).andExpect(status().isUnauthorized());

        MockHttpServletResponse first = mockMvc.perform(refresh(browser.refresh(), browser.csrf()).cookie(forged))
                .andExpect(status().isOk()).andReturn().getResponse();
        mockMvc.perform(refresh(cookie(first, MemberAuthCookies.REFRESH_TOKEN), browser.csrf()).cookie(expired))
                .andExpect(status().isOk());
    }

    @Test
    void repeatedRotationNeverExtendsTheAbsoluteExpiry() throws Exception { // MB-T035
        Browser browser = loggedIn("tester01");
        String sid = onlySessionId();
        long expiry = sessionExpiry(sid);
        Cookie refresh = browser.refresh();

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = mockMvc.perform(refresh(refresh, browser.csrf()))
                    .andExpect(status().isOk()).andReturn().getResponse();
            refresh = cookie(response, MemberAuthCookies.REFRESH_TOKEN);
            assertThat(sessionExpiry(sid)).isEqualTo(expiry);
            assertThat(refreshExpiry(refresh)).isEqualTo(expiry);
        }
    }

    @Test
    void oldRefreshAfterRotationRevokesTheSessionEvenIfTheResponseWasLost() throws Exception { // MB-T036
        Browser browser = loggedIn("tester01");
        MockHttpServletResponse rotated = mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie latestAccess = cookie(rotated, AuthCookieNames.ACCESS_TOKEN);
        Cookie latestRefresh = cookie(rotated, MemberAuthCookies.REFRESH_TOKEN);

        // 응답을 못 받은 클라이언트가 이전 Refresh로 다시 시도 = 재사용
        MockHttpServletResponse reuse = mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"))
                .andReturn().getResponse();

        assertAuthCookiesCleared(reuse);
        assertThat(redis.keys("auth:session:*")).isEmpty();
        mockMvc.perform(get("/test/protected/me").cookie(latestAccess)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/test/protected/me").cookie(browser.access())).andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(latestRefresh, browser.csrf())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void concurrentRefreshesWithOneTokenLetAtMostOneSucceedAndRevokeTheSession() throws Exception { // MB-T037
        Browser browser = loggedIn("tester01");
        int threads = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    return mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<Integer> future : futures) {
                statuses.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(s -> s == 200).hasSizeLessThanOrEqualTo(1);
            assertThat(statuses).allMatch(s -> s == 200 || s == 401);
            // 나머지 요청이 재사용으로 탐지돼 세션이 폐기된다(성공한 쪽의 새 Refresh도 쓸 수 없다)
            assertThat(redis.keys("auth:session:*")).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void missingOrUnknownRefreshIsInvalidAndClearsCookies() throws Exception { // MB-T040
        MockHttpServletResponse none = mockMvc.perform(post("/api/v1/auth/refresh").with(RealCsrf.token(csrfToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"))
                .andReturn().getResponse();
        assertAuthCookiesCleared(none);

        Cookie unknown = new Cookie(MemberAuthCookies.REFRESH_TOKEN, "A".repeat(43));
        mockMvc.perform(refresh(unknown, csrfToken)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void disabledMemberIsForbiddenAndSessionRevoked() throws Exception { // MB-T038
        Browser browser = loggedIn("tester01");
        jdbc.update("UPDATE members SET account_status = 'DISABLED' WHERE id = ?", browser.memberId());

        MockHttpServletResponse response = mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_ACCOUNT_DISABLED"))
                .andReturn().getResponse();

        assertAuthCookiesCleared(response);
        assertThat(redis.keys("auth:session:*")).isEmpty();
        mockMvc.perform(get("/test/protected/me").cookie(browser.access())).andExpect(status().isUnauthorized());
    }

    @Test
    void pendingMemberIsRejectedBeforeAndAfterPendingExpiry() throws Exception { // MB-T038
        Browser browser = loggedIn("tester01");
        jdbc.update("UPDATE members SET account_status = 'PENDING' WHERE id = ?", browser.memberId());

        MockHttpServletResponse response = mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"))
                .andReturn().getResponse();
        assertAuthCookiesCleared(response);
        assertThat(redis.keys("auth:session:*")).isEmpty();

        Browser second = loggedIn("tester02");
        jdbc.update("UPDATE members SET account_status = 'PENDING', created_at = now() - interval '2 days' "
                + "WHERE id = ?", second.memberId());
        mockMvc.perform(refresh(second.refresh(), second.csrf())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void missingMemberIsRejectedAndSessionRevoked() throws Exception { // MB-T038
        Browser browser = loggedIn("tester01");
        jdbc.update("DELETE FROM members WHERE id = ?", browser.memberId());

        mockMvc.perform(refresh(browser.refresh(), browser.csrf())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void refreshWithoutCsrfIsForbiddenAndChangesNothing() throws Exception { // MB-T040
        Browser browser = loggedIn("tester01");
        String sid = onlySessionId();
        Object hashBefore = redis.opsForHash().get("auth:session:" + sid, "refreshHash");

        // XSRF 쿠키는 있지만 헤더가 없다(이중 제출 실패)
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(browser.access(), browser.refresh(), new Cookie(RealCsrf.COOKIE, browser.csrf())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"))
                .andReturn().getResponse();

        assertNoAuthCookieChanges(response);
        assertThat(redis.opsForHash().get("auth:session:" + sid, "refreshHash")).isEqualTo(hashBefore);
    }

    @Test
    void refreshIsLimitedPerIpAndKeepsCookies() throws Exception { // MB-T040
        RequestPostProcessor ip = request -> {
            request.setRemoteAddr("10.9.9.9");
            return request;
        };
        for (int i = 0; i < 120; i++) {
            mockMvc.perform(post("/api/v1/auth/refresh").with(RealCsrf.token(csrfToken)).with(ip))
                    .andExpect(status().isUnauthorized());
        }

        MockHttpServletResponse limited = mockMvc.perform(post("/api/v1/auth/refresh").with(RealCsrf.token(csrfToken))
                        .with(ip).header("X-Forwarded-For", "1.2.3.4"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON_TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse();

        assertNoAuthCookieChanges(limited);
    }

    // ----- 로그아웃 -----

    @Test
    void logoutRevokesTheSessionClearsCookiesAndRenewsCsrf() throws Exception { // MB-T041
        Browser browser = loggedIn("tester01");

        MockHttpServletResponse response = mockMvc.perform(logout(browser.csrf()).cookie(browser.access(),
                        browser.refresh()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getContentLength()).isZero();
        assertAuthCookiesCleared(response);
        assertThat(setCookie(response, MemberAuthCookies.OTP_CHALLENGE)).contains("Max-Age=0").contains("Path=/")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict");
        Cookie renewed = response.getCookie(RealCsrf.COOKIE);
        assertThat(renewed).isNotNull();
        assertThat(renewed.getValue()).isNotBlank().isNotEqualTo(browser.csrf());
        assertThat(redis.keys("auth:session:*")).isEmpty();
        assertThat(redis.keys("auth:refresh:*")).isEmpty();
        mockMvc.perform(get("/test/protected/me").cookie(browser.access())).andExpect(status().isUnauthorized());
        mockMvc.perform(refresh(browser.refresh(), browser.csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCookiesIsIdempotent() throws Exception { // MB-T041
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = mockMvc.perform(logout(csrfToken))
                    .andExpect(status().isNoContent()).andReturn().getResponse();
            assertAuthCookiesCleared(response);
        }
    }

    @Test
    void logoutWithOnlyAccessRevokesItsSession() throws Exception { // MB-T041
        Browser browser = loggedIn("tester01");

        mockMvc.perform(logout(browser.csrf()).cookie(browser.access())).andExpect(status().isNoContent());

        assertThat(redis.keys("auth:session:*")).isEmpty();
        mockMvc.perform(refresh(browser.refresh(), browser.csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithExpiredAccessUsesTheRefreshCookie() throws Exception { // MB-T041
        Browser browser = loggedIn("tester01");
        Cookie expired = new Cookie(AuthCookieNames.ACCESS_TOKEN, accessTokenIssuer
                .issue(browser.memberId(), "USER", onlySessionId(), Instant.now().plusMillis(1100)).value());
        Thread.sleep(1500);

        mockMvc.perform(logout(browser.csrf()).cookie(expired, browser.refresh())).andExpect(status().isNoContent());

        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void logoutWithAUsedRefreshTokenRevokesItsSession() throws Exception { // MB-T041
        Browser browser = loggedIn("tester01");
        mockMvc.perform(refresh(browser.refresh(), browser.csrf())).andExpect(status().isOk());

        mockMvc.perform(logout(browser.csrf()).cookie(browser.refresh())).andExpect(status().isNoContent());

        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void logoutRevokesBothSessionsWhenAccessAndRefreshDiffer() throws Exception { // MB-T041
        Browser first = loggedIn("tester01");
        Browser second = loggedIn("tester02");
        assertThat(redis.keys("auth:session:*")).hasSize(2);

        mockMvc.perform(logout(second.csrf()).cookie(first.access(), second.refresh()))
                .andExpect(status().isNoContent());

        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void logoutWithoutCsrfIsForbiddenAndKeepsTheSession() throws Exception { // MB-T041
        Browser browser = loggedIn("tester01");

        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(browser.access(), browser.refresh(), new Cookie(RealCsrf.COOKIE, browser.csrf())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"))
                .andReturn().getResponse();

        assertNoAuthCookieChanges(response);
        assertThat(redis.keys("auth:session:*")).hasSize(1);
        mockMvc.perform(get("/test/protected/me").cookie(browser.access())).andExpect(status().isOk());
    }

    // ----- 비밀 값 -----

    @Test
    void secretsNeverAppearInRefreshOrLogoutBodiesOrLogs(CapturedOutput output) throws Exception { // MB-T042
        Browser browser = loggedIn("tester01");
        String sid = onlySessionId();
        MockHttpServletResponse refreshed = mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        String newAccess = cookie(refreshed, AuthCookieNames.ACCESS_TOKEN).getValue();
        String newRefresh = cookie(refreshed, MemberAuthCookies.REFRESH_TOKEN).getValue();
        Map<String, Object> json = com.jayway.jsonpath.JsonPath
                .parse(refreshed.getContentAsString(StandardCharsets.UTF_8)).read("$");
        assertThat(json).containsOnlyKeys("memberId", "role", "accessTokenExpiresAt");

        MockHttpServletResponse reuse = mockMvc.perform(refresh(browser.refresh(), browser.csrf()))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        Map<String, Object> problem = com.jayway.jsonpath.JsonPath
                .parse(reuse.getContentAsString(StandardCharsets.UTF_8)).read("$");

        // 같은 초에 발급된 Access는 Claim이 같아 값이 같을 수 있다(집합으로 중복 제거)
        Set<String> secrets = new java.util.HashSet<>(List.of(browser.refresh().getValue(),
                sha256(browser.refresh().getValue()), browser.access().getValue(), newAccess, newRefresh,
                sha256(newRefresh), sid));
        assertThat(json.values()).map(String::valueOf).noneMatch(secrets::contains);
        assertThat(problem.values()).map(String::valueOf)
                .noneMatch(value -> secrets.stream().anyMatch(value::contains));
        assertThat(output).contains("Refresh Token 재사용");
        for (String secret : secrets) {
            assertThat(output.getAll()).doesNotContain(secret);
        }
    }

    // ----- 도우미 -----

    private Browser loggedIn(String username) throws Exception {
        String email = username + "@example.com";
        String body = """
                {"username":"%s","password":"%s","passwordConfirm":"%s","email":"%s","nickname":"테스터"}
                """.formatted(username, PASSWORD, PASSWORD, email);
        String signup = mockMvc.perform(post("/api/v1/auth/signup").with(RealCsrf.token(csrfToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long memberId = Long.parseLong(signup.replaceAll(".*\"memberId\":(\\d+).*", "$1"));
        MockHttpServletResponse login = mockMvc.perform(post("/api/v1/auth/login").with(RealCsrf.token(csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie challenge = cookie(login, MemberAuthCookies.OTP_CHALLENGE);
        MockHttpServletResponse verified = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .with(RealCsrf.token(csrfToken)).cookie(challenge)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + mail.lastCodeFor(email) + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new Browser(memberId, cookie(verified, AuthCookieNames.ACCESS_TOKEN),
                cookie(verified, MemberAuthCookies.REFRESH_TOKEN), cookie(verified, RealCsrf.COOKIE).getValue());
    }

    private static MockHttpServletRequestBuilder refresh(Cookie refreshCookie, String csrf) {
        return post("/api/v1/auth/refresh").with(RealCsrf.token(csrf)).cookie(refreshCookie);
    }

    private static MockHttpServletRequestBuilder logout(String csrf) {
        return post("/api/v1/auth/logout").with(RealCsrf.token(csrf));
    }

    private String onlySessionId() {
        Set<String> keys = redis.keys("auth:session:*");
        assertThat(keys).hasSize(1);
        return keys.iterator().next().substring("auth:session:".length());
    }

    private long sessionExpiry(String sid) {
        return pexpiretime("auth:session:" + sid);
    }

    private long refreshExpiry(Cookie refresh) {
        return pexpiretime("auth:refresh:" + sha256(refresh.getValue()));
    }

    private long pexpiretime(String key) {
        return redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "return redis.call('PEXPIRETIME', KEYS[1])", Long.class), List.of(key));
    }

    private static void assertAuthCookiesCleared(MockHttpServletResponse response) {
        assertThat(setCookie(response, AuthCookieNames.ACCESS_TOKEN)).contains("Max-Age=0").contains("Path=/")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict").doesNotContain("Domain=");
        assertThat(setCookie(response, MemberAuthCookies.REFRESH_TOKEN)).contains("Max-Age=0")
                .contains("Path=/api/v1/auth").contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .doesNotContain("Domain=");
    }

    private static void assertNoAuthCookieChanges(MockHttpServletResponse response) {
        assertThat(response.getHeaders("Set-Cookie"))
                .noneMatch(value -> value.startsWith(AuthCookieNames.ACCESS_TOKEN + "="))
                .noneMatch(value -> value.startsWith(MemberAuthCookies.REFRESH_TOKEN + "="))
                .noneMatch(value -> value.startsWith(RealCsrf.COOKIE + "="));
    }

    private static Cookie cookie(MockHttpServletResponse response, String name) {
        Cookie cookie = response.getCookie(name);
        assertThat(cookie).as("쿠키 " + name).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private static String setCookie(MockHttpServletResponse response, String name) {
        return response.getHeaders("Set-Cookie").stream().filter(value -> value.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie 없음: " + name));
    }

    private static long maxAge(MockHttpServletResponse response, String name) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Max-Age=(\\d+)")
                .matcher(setCookie(response, name));
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
