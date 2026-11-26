package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.in.MemberAuthCookies;
import com.yhs.livestreaming.member.app.password.MemberPasswordProperties;
import com.yhs.livestreaming.member.app.password.Pbkdf2PasswordHasher;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import testsupport.AppIntegrationTest;
import testsupport.CapturingOtpMailSender;
import testsupport.RealCsrf;
import testsupport.SecurityTestEndpoints;

/**
 * 로그인 1차 인증 → 이메일 OTP → 토큰 쿠키 발급(MB-W05~W08) 통합 테스트. 실제 보안 체인·PostgreSQL·Redis(Testcontainers)를
 * 쓰고 메일·HIBP는 대역이다. 테스트 ID는 docs/member/test-spec.md(MB-T018~MB-T034).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
@Import(SecurityTestEndpoints.class)
@ExtendWith(OutputCaptureExtension.class)
class LoginApiIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String EMAIL = "tester@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CapturingOtpMailSender mail;

    /** 실제 초기 발급 API로 받은 CSRF 토큰(G-D014). spring-security-test의 csrf()는 공유 컨텍스트의 저장소를 바꿔 쓰지 않는다. */
    private String csrfToken;

    @BeforeEach
    void issueCsrfToken() throws Exception {
        csrfToken = RealCsrf.issue(mockMvc);
    }

    private RequestPostProcessor realCsrf() {
        return RealCsrf.token(csrfToken);
    }

    @AfterEach
    void cleanUp() {
        mail.reset();
        jdbc.update("DELETE FROM member_oauth_accounts");
        jdbc.update("DELETE FROM members");
        redis.delete(redis.keys("*"));
    }

    @Test
    void passwordStepIssuesOnlyAChallengeCookieAndMailsAFourDigitOtp() throws Exception { // MB-T018
        signup("tester01", EMAIL);

        MockHttpServletResponse response = login("tester01", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OTP_REQUIRED"))
                .andExpect(jsonPath("$.otpExpiresAt").isNotEmpty())
                .andReturn().getResponse();

        assertThat(setCookie(response, MemberAuthCookies.OTP_CHALLENGE))
                .contains("Path=/").contains("Secure").contains("HttpOnly")
                .contains("SameSite=Strict").doesNotContain("Domain=");
        assertThat(maxAge(response, MemberAuthCookies.OTP_CHALLENGE)).isBetween(600L - MAX_AGE_SLACK, 600L);
        assertThat(response.getHeaders("Set-Cookie"))
                .noneMatch(value -> value.startsWith(AuthCookieNames.ACCESS_TOKEN))
                .noneMatch(value -> value.startsWith(MemberAuthCookies.REFRESH_TOKEN));
        assertThat(mail.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.email()).isEqualTo(EMAIL);
            assertThat(sent.code()).matches("[0-9]{4}");
        });
    }

    @Test
    void otpSuccessActivatesPendingMemberAndIssuesAuthCookies() throws Exception { // MB-T022
        long memberId = signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());
        String csrfBefore = csrfToken;

        MockHttpServletResponse response = mockMvc.perform(verify(challenge, mail.lastCodeFor(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.accessTokenExpiresAt").isNotEmpty())
                .andReturn().getResponse();

        assertThat(setCookie(response, AuthCookieNames.ACCESS_TOKEN)).contains("Path=/")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict").doesNotContain("Domain=");
        assertThat(maxAge(response, AuthCookieNames.ACCESS_TOKEN)).isBetween(900L - MAX_AGE_SLACK, 900L);
        assertThat(setCookie(response, MemberAuthCookies.REFRESH_TOKEN)).contains("Path=/api/v1/auth")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict");
        assertThat(maxAge(response, MemberAuthCookies.REFRESH_TOKEN)).isBetween(604800L - MAX_AGE_SLACK, 604800L);
        assertThat(setCookie(response, MemberAuthCookies.OTP_CHALLENGE)).contains("Max-Age=0").contains("Path=/");
        Cookie renewedCsrf = response.getCookie("XSRF-TOKEN");
        assertThat(renewedCsrf).isNotNull();
        assertThat(renewedCsrf.getValue()).isNotBlank().isNotEqualTo(csrfBefore);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM members WHERE id = ?", memberId);
        assertThat(row.get("account_status")).isEqualTo("ACTIVE");
        assertThat(row.get("email_verified")).isEqualTo(true);

        String refresh = response.getCookie(MemberAuthCookies.REFRESH_TOKEN).getValue();
        assertThat(redis.keys("auth:refresh:*")).containsExactly("auth:refresh:" + sha256(refresh));
        mockMvc.perform(get("/test/protected/me").cookie(response.getCookie(AuthCookieNames.ACCESS_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(content().string(Long.toString(memberId)));
    }

    @Test
    void challengeIsConsumedAndCannotBeReused() throws Exception { // MB-T022
        signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());
        String code = mail.lastCodeFor(EMAIL);
        mockMvc.perform(verify(challenge, code)).andExpect(status().isOk());

        mockMvc.perform(verify(challenge, code))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_CHALLENGE_INVALID"));
    }

    @Test
    void concurrentVerificationsOfOneChallengeLetOnlyOneSucceed() throws Exception { // MB-T022
        signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());
        String code = mail.lastCodeFor(EMAIL);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<MockHttpServletResponse>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    assertThat(start.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    return mockMvc.perform(verify(challenge, code)).andReturn().getResponse();
                }));
            }
            start.countDown();
            List<MockHttpServletResponse> responses = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS)); // 교착 시 빌드가 멈추지 않게
            }

            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 401);
            assertThat(responses).filteredOn(r -> r.getStatus() == 401).singleElement()
                    .satisfies(r -> assertThat(r.getContentAsString(StandardCharsets.UTF_8))
                            .contains("AUTH_OTP_CHALLENGE_INVALID"));
            assertThat(responses).filteredOn(r -> r.getCookie(AuthCookieNames.ACCESS_TOKEN) != null).hasSize(1);
            assertThat(redis.keys("auth:session:*")).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void wrongOtpFiveTimesExhaustsTheOtp() throws Exception { // MB-T023
        signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());
        String code = mail.lastCodeFor(EMAIL);
        String wrong = code.equals("0000") ? "0001" : "0000";
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(verify(challenge, wrong))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_OTP_MISMATCH"));
        }
        mockMvc.perform(verify(challenge, wrong))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_EXPIRED"))
                .andExpect(jsonPath("$.detail").value(MemberErrorCode.OTP_EXHAUSTED_DETAIL));

        mockMvc.perform(verify(challenge, code))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_EXPIRED"));
    }

    @Test
    void malformedOtpIsRejectedAsInvalidInput() throws Exception {
        signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());

        mockMvc.perform(verify(challenge, "12a4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void challengeCookieCannotAuthenticateProtectedApis() throws Exception { // MB-T023
        signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());

        mockMvc.perform(get("/test/protected/me").cookie(challenge))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/test/protected/me")
                        .cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, challenge.getValue())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyWithoutChallengeCookieAsksForNewLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/verify").with(realCsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_CHALLENGE_INVALID"));
    }

    @Test
    void failuresLookTheSameForWrongPasswordAndUnknownUsername() throws Exception { // MB-T019
        signup("tester01", EMAIL);

        String wrongPassword = login("tester01", "wrong password, still long").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknown = login("nobody99", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(withoutInstance(unknown)).isEqualTo(withoutInstance(wrongPassword));
        assertThat(wrongPassword).contains("AUTH_LOGIN_FAILED");
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void disabledMemberWithCorrectPasswordIsForbiddenAndNoMailIsSent() throws Exception { // MB-T019
        long memberId = signup("tester01", EMAIL);
        jdbc.update("UPDATE members SET account_status = 'DISABLED' WHERE id = ?", memberId);

        login("tester01", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_ACCOUNT_DISABLED"));
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void tenthFailureBlocksEvenTheCorrectPassword() throws Exception { // MB-T020
        signup("tester01", EMAIL);
        for (int i = 0; i < 9; i++) {
            login("tester01", "wrong password, still long").andExpect(status().isUnauthorized());
        }

        login("tester01", "wrong password, still long")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_TEMPORARILY_BLOCKED"));
        login("tester01", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_TEMPORARILY_BLOCKED"));
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void secondLoginWithinSixtySecondsIsLimitedAndResendNeedsTheInterval() throws Exception { // MB-T024·T025
        signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());
        String firstCode = mail.lastCodeFor(EMAIL);

        login("tester01", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("AUTH_OTP_RATE_LIMITED"));
        mockMvc.perform(resend(challenge))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_RATE_LIMITED"));

        redis.delete(redis.keys("auth:otp:member:*:last-issued")); // 60초 경과
        mockMvc.perform(resend(challenge))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OTP_REQUIRED"))
                .andExpect(jsonPath("$.otpExpiresAt").isNotEmpty());
        String secondCode = mail.lastCodeFor(EMAIL);

        assertThat(secondCode).isNotEqualTo(firstCode);
        mockMvc.perform(verify(challenge, firstCode))
                .andExpect(jsonPath("$.code").value("AUTH_OTP_MISMATCH"));
        mockMvc.perform(verify(challenge, secondCode)).andExpect(status().isOk());
    }

    @Test
    void mailFailureDuringLoginIsServiceUnavailableWithoutChallenge(CapturedOutput output) throws Exception { // MB-T026
        signup("tester01", EMAIL);
        mail.failing(true);

        MockHttpServletResponse response = login("tester01", PASSWORD)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEMBER_OTP_DELIVERY_UNAVAILABLE"))
                .andReturn().getResponse();

        assertThat(response.getHeaders("Set-Cookie")).noneMatch(value -> value.startsWith("__Host-"));
        assertThat(redis.keys("auth:otp:challenge:*")).isEmpty();
        // 대역이 던진 예외 메시지("smtp unavailable for <이메일> code <OTP>")가 응답·로그로 새지 않는다
        String failedCode = mail.lastFailedCode();
        assertThat(failedCode).matches("[0-9]{4}");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(EMAIL).doesNotContain(CapturingOtpMailSender.FAILURE_MESSAGE_PREFIX)
                .doesNotContain("code " + failedCode);
        assertThat(output.getAll()).doesNotContain(EMAIL).doesNotContain(CapturingOtpMailSender.FAILURE_MESSAGE_PREFIX)
                .doesNotContain("code " + failedCode);
    }

    @Test
    void memberDisabledOrExpiredWhileWaitingForOtpGetsNoTokens() throws Exception { // MB-T030
        long memberId = signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());
        jdbc.update("UPDATE members SET created_at = created_at - INTERVAL '25 hours' WHERE id = ?", memberId);

        MockHttpServletResponse response = mockMvc.perform(verify(challenge, mail.lastCodeFor(EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"))
                .andReturn().getResponse();

        assertThat(response.getHeaders("Set-Cookie")).noneMatch(value -> value.startsWith(AuthCookieNames.ACCESS_TOKEN));
        // Challenge는 이미 소비됐으므로 쿠키도 지운다(남은 쿠키는 쓸모가 없다)
        assertThat(setCookie(response, MemberAuthCookies.OTP_CHALLENGE)).contains("Max-Age=0").contains("Path=/");
        assertThat(jdbc.queryForObject("SELECT account_status FROM members WHERE id = ?", String.class, memberId))
                .isEqualTo("PENDING");
        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void signupIsLimitedPerIpAndForwardedHeadersAreIgnored() throws Exception { // MB-T028
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(signupRequest("user000" + i, "user" + i + "@example.com"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(signupRequest("user0006", "user6@example.com").header("X-Forwarded-For", "198.51.100.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("COMMON_TOO_MANY_REQUESTS"));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester01\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
    }

    @Test
    void passwordsWithOnlySpacesAreAcceptedAsInputLikeSignup() throws Exception {
        login("tester01", "               ")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"));
    }

    @Test
    void outdatedHashIsUpgradedAfterSuccessfulPasswordStep() throws Exception { // MB-T031
        long memberId = signup("tester01", EMAIL);
        Pbkdf2PasswordHasher weaker = new Pbkdf2PasswordHasher(new MemberPasswordProperties(
                "7Z6RRuZSnLyTcuYWD+AVthYIE51XWaIGsUbYwbOYuU4=", "test-key-1", 1_000));
        jdbc.update("UPDATE members SET password_hash = ? WHERE id = ?", weaker.hash(PASSWORD), memberId);

        login("tester01", PASSWORD).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, memberId))
                .startsWith("pbkdf2-sha256$v1$nfc$i=600000$");
    }

    @Test
    void secretsNeverAppearInLogsOrBodies(CapturedOutput output) throws Exception { // MB-T034
        signup("tester01", EMAIL);
        Cookie challenge = challengeCookie(login("tester01", PASSWORD).andReturn());
        String code = mail.lastCodeFor(EMAIL);
        MockHttpServletResponse response = mockMvc.perform(verify(challenge, code)).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        String access = response.getCookie(AuthCookieNames.ACCESS_TOKEN).getValue();
        String refresh = response.getCookie(MemberAuthCookies.REFRESH_TOKEN).getValue();

        Map<String, Object> json = com.jayway.jsonpath.JsonPath.parse(body).read("$");
        assertThat(json).containsOnlyKeys("memberId", "role", "accessTokenExpiresAt");
        assertThat(json.values()).map(String::valueOf)
                .noneMatch(value -> value.equals(code) || value.equals(access) || value.equals(refresh)
                        || value.equals(challenge.getValue()) || value.equals(PASSWORD));
        assertThat(output).doesNotContain(PASSWORD).doesNotContain(access).doesNotContain(refresh)
                .doesNotContain(challenge.getValue()).doesNotContain("code " + code);
    }

    private long signup(String username, String email) throws Exception {
        String body = mockMvc.perform(signupRequest(username, email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Long.parseLong(body.replaceAll(".*\"memberId\":(\\d+).*", "$1"));
    }

    private MockHttpServletRequestBuilder signupRequest(String username, String email) {
        String body = """
                {"username":"%s","password":"%s","passwordConfirm":"%s","email":"%s","nickname":"테스터"}
                """.formatted(username, PASSWORD, PASSWORD, email);
        return post("/api/v1/auth/signup").with(realCsrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(realCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private MockHttpServletRequestBuilder verify(Cookie challenge, String code) {
        return post("/api/v1/auth/otp/verify").with(realCsrf()).cookie(challenge).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}");
    }

    private MockHttpServletRequestBuilder resend(Cookie challenge) {
        return post("/api/v1/auth/otp/resend").with(realCsrf()).cookie(challenge);
    }

    private static Cookie challengeCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(MemberAuthCookies.OTP_CHALLENGE);
        assertThat(cookie).as("Challenge 쿠키").isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private static String setCookie(MockHttpServletResponse response, String name) {
        List<String> headers = response.getHeaders("Set-Cookie");
        return headers.stream().filter(value -> value.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie 없음: " + name));
    }

    /** 발급 시각과 쿠키를 쓰는 시각 사이에 흐를 수 있는 시간(초). Max-Age는 남은 시간을 올림한 값이다. */
    private static final long MAX_AGE_SLACK = 10;

    private static long maxAge(MockHttpServletResponse response, String name) {
        String header = setCookie(response, name);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Max-Age=(\\d+)").matcher(header);
        assertThat(matcher.find()).as("Max-Age: " + name).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static String withoutInstance(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\"", "");
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
