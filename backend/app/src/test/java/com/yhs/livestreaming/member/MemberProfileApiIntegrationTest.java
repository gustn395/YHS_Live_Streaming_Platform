package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.global.auth.AuthCookieNames;
import com.yhs.livestreaming.member.in.MemberAuthCookies;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * MB-T055~MB-T057: 내 정보 조회·닉네임 수정 API(MB-D028). 실제 로그인(비밀번호 → OTP)으로 받은 쿠키와 실제 보안 체인·
 * PostgreSQL·Redis를 쓴다. 세션 저장소 장애 503은 global 공통 경로(GL 테스트, RedisUnavailableIntegrationTest)가 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
class MemberProfileApiIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String ME = "/api/v1/members/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CapturingOtpMailSender mail;

    @Autowired
    private com.yhs.livestreaming.member.app.session.LoginSessionStore loginSessions;

    private String csrfToken;

    /** 로그인을 마친 브라우저 한 개의 쿠키 상태. */
    record Browser(long memberId, Cookie access, String csrf) {
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

    @Test
    void loggedInLocalMemberGetsOwnProfile() throws Exception { // MB-T055
        Browser browser = loggedIn("tester01");

        String body = mockMvc.perform(get(ME).cookie(browser.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(browser.memberId()))
                .andExpect(jsonPath("$.accountType").value("LOCAL"))
                .andExpect(jsonPath("$.username").value("tester01"))
                .andExpect(jsonPath("$.email").value("tester01@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.nickname").value("테스터"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        MockHttpServletResponse headers = mockMvc.perform(get(ME).cookie(browser.access())).andReturn().getResponse();
        assertThat(headers.getHeader("Cache-Control")).contains("no-store"); // 개인정보 응답을 캐시하지 않는다

        Map<String, Object> json = com.jayway.jsonpath.JsonPath.parse(body).read("$");
        assertThat(json).containsOnlyKeys("memberId", "accountType", "username", "email", "emailVerified",
                "nickname", "role", "accountStatus");
    }

    @Test
    void googleMemberProfileHasNullUsername() throws Exception { // MB-T055
        Browser browser = loggedIn("tester01");
        jdbc.update("UPDATE members SET account_type = 'GOOGLE', username = NULL, password_hash = NULL WHERE id = ?",
                browser.memberId());

        mockMvc.perform(get(ME).cookie(browser.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("GOOGLE"))
                .andExpect(jsonPath("$.username").isEmpty());
    }

    @Test
    void anonymousRequestIsUnauthenticated() throws Exception { // MB-T055
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void disabledMemberIsForbiddenAndLosesSessionAndCookies() throws Exception { // MB-T056
        Browser browser = loggedIn("tester01");
        jdbc.update("UPDATE members SET account_status = 'DISABLED' WHERE id = ?", browser.memberId());

        MockHttpServletResponse response = mockMvc.perform(get(ME).cookie(browser.access()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_ACCOUNT_DISABLED"))
                .andReturn().getResponse();

        assertAuthCookiesCleared(response);
        assertThat(redis.keys("auth:session:*")).isEmpty();
        mockMvc.perform(get(ME).cookie(browser.access())).andExpect(status().isUnauthorized());
    }

    @Test
    void pendingMemberIsUnauthenticatedAndLosesSessionAndCookies() throws Exception { // MB-T056(정상 흐름 밖의 상태)
        Browser browser = loggedIn("tester01");
        jdbc.update("UPDATE members SET account_status = 'PENDING' WHERE id = ?", browser.memberId());

        MockHttpServletResponse response = mockMvc.perform(get(ME).cookie(browser.access()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"))
                .andReturn().getResponse();

        assertAuthCookiesCleared(response);
        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void googleMemberChangesNickname() throws Exception { // MB-T057: LOCAL·GOOGLE 공통
        Browser browser = loggedIn("tester01");
        jdbc.update("UPDATE members SET account_type = 'GOOGLE', username = NULL, password_hash = NULL WHERE id = ?",
                browser.memberId());

        mockMvc.perform(changeNickname(browser, "{\"nickname\":\"구글 닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("구글 닉네임"))
                .andExpect(jsonPath("$.username").isEmpty());
    }

    @Test
    void disablingRevokesOnlyTheRequestingSession() throws Exception { // MB-T056: 다른 세션·다른 회원의 Challenge는 그대로
        Browser browser = loggedIn("tester01");
        String requestingSid = onlySessionId();
        loginSessions.create(new com.yhs.livestreaming.member.app.session.NewLoginSession("O".repeat(43),
                browser.memberId(), "a".repeat(64), java.time.Instant.now().plus(java.time.Duration.ofDays(1))));
        signupAndStartLogin("tester02");
        java.util.Set<String> challengesBefore = redis.keys("auth:otp:challenge:*");
        assertThat(challengesBefore).isNotEmpty();
        jdbc.update("UPDATE members SET account_status = 'DISABLED' WHERE id = ?", browser.memberId());

        mockMvc.perform(get(ME).cookie(browser.access())).andExpect(status().isForbidden());

        assertThat(redis.keys("auth:session:*")).containsExactly("auth:session:" + "O".repeat(43))
                .doesNotContain("auth:session:" + requestingSid);
        assertThat(redis.keys("auth:otp:challenge:*")).isEqualTo(challengesBefore);
    }

    @Test
    void deletedMemberIsUnauthenticatedAndLosesSessionAndCookies() throws Exception { // MB-T056
        Browser browser = loggedIn("tester01");
        jdbc.update("DELETE FROM members WHERE id = ?", browser.memberId());

        MockHttpServletResponse response = mockMvc.perform(get(ME).cookie(browser.access()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"))
                .andReturn().getResponse();

        assertAuthCookiesCleared(response);
        assertThat(redis.keys("auth:session:*")).isEmpty();
    }

    @Test
    void nicknameChangeIsSavedAndOtherFieldsStay() throws Exception { // MB-T057
        Browser browser = loggedIn("tester01");

        mockMvc.perform(changeNickname(browser,
                        "{\"nickname\":\"  새 닉네임  \",\"username\":\"hacker\",\"email\":\"x@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새 닉네임"))
                .andExpect(jsonPath("$.username").value("tester01"))
                .andExpect(jsonPath("$.email").value("tester01@example.com"));

        Map<String, Object> row = jdbc.queryForMap("SELECT username, email, nickname FROM members WHERE id = ?",
                browser.memberId());
        assertThat(row).containsEntry("nickname", "새 닉네임").containsEntry("username", "tester01")
                .containsEntry("email", "tester01@example.com");
    }

    @Test
    void invalidNicknameIsBadRequest() throws Exception { // MB-T057: MB-D008 닉네임 규칙
        Browser browser = loggedIn("tester01");

        for (String nickname : new String[] {"a", " ", "제어\\u0007문자", "가".repeat(21)}) {
            mockMvc.perform(changeNickname(browser, "{\"nickname\":\"" + nickname + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors[?(@.field == 'nickname')]").exists());
        }
        assertThat(jdbc.queryForObject("SELECT nickname FROM members WHERE id = ?", String.class,
                browser.memberId())).isEqualTo("테스터");
    }

    @Test
    void nicknameChangeNeedsCsrfToken() throws Exception { // MB-T057(G-D014)
        Browser browser = loggedIn("tester01");

        mockMvc.perform(patch(ME).cookie(browser.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새 닉네임\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        assertThat(jdbc.queryForObject("SELECT nickname FROM members WHERE id = ?", String.class,
                browser.memberId())).isEqualTo("테스터");
    }

    @Test
    void disabledMemberCannotChangeNickname() throws Exception { // MB-T057·MB-T056
        Browser browser = loggedIn("tester01");
        jdbc.update("UPDATE members SET account_status = 'DISABLED' WHERE id = ?", browser.memberId());

        MockHttpServletResponse response = mockMvc.perform(changeNickname(browser, "{\"nickname\":\"새 닉네임\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_ACCOUNT_DISABLED"))
                .andReturn().getResponse();

        assertAuthCookiesCleared(response);
        assertThat(jdbc.queryForObject("SELECT nickname FROM members WHERE id = ?", String.class,
                browser.memberId())).isEqualTo("테스터");
    }

    // ----- 도우미 -----

    private MockHttpServletRequestBuilder changeNickname(Browser browser, String body) {
        return patch(ME).with(RealCsrf.token(browser.csrf())).cookie(browser.access())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String onlySessionId() {
        java.util.Set<String> keys = redis.keys("auth:session:*");
        assertThat(keys).hasSize(1);
        return keys.iterator().next().substring("auth:session:".length());
    }

    /** 가입하고 비밀번호 1차 인증까지만 해 OTP Challenge를 만든다. */
    private void signupAndStartLogin(String username) throws Exception {
        String body = """
                {"username":"%s","password":"%s","passwordConfirm":"%s","email":"%s@example.com","nickname":"테스터"}
                """.formatted(username, PASSWORD, PASSWORD, username);
        mockMvc.perform(post("/api/v1/auth/signup").with(RealCsrf.token(csrfToken))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/login").with(RealCsrf.token(csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

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
                cookie(verified, RealCsrf.COOKIE).getValue());
    }

    private static void assertAuthCookiesCleared(MockHttpServletResponse response) {
        assertThat(setCookie(response, AuthCookieNames.ACCESS_TOKEN)).contains("Max-Age=0").contains("Path=/");
        assertThat(setCookie(response, MemberAuthCookies.REFRESH_TOKEN)).contains("Max-Age=0")
                .contains("Path=/api/v1/auth");
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
}
