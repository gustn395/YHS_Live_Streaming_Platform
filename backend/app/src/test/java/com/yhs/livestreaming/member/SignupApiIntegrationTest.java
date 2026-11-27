package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.yhs.livestreaming.member.app.password.BreachedPasswordChecker.Result;
import testsupport.AppIntegrationTest;
import testsupport.RealCsrf;
import testsupport.StubBreachedPasswordChecker;

/**
 * 회원가입 API(MB-W04) 통합 테스트. 실제 보안 체인·Testcontainers PostgreSQL·Flyway 스키마를 쓴다.
 * 테스트 ID는 docs/member/test-spec.md(MB-T001~), 정책은 MB-D003·MB-D004·MB-D005·MB-D008·MB-D010.
 * 유출 비밀번호 검사는 대역({@link StubBreachedPasswordChecker})을 쓰고 실제 HIBP를 부르지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SignupApiIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_IP = new java.util.concurrent.atomic.AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StubBreachedPasswordChecker breachedPasswordChecker;

    /** 실제 발급 API의 CSRF 토큰(G-D014). 가입 응답에 Set-Cookie가 없는지도 실제 쿠키 저장소로 확인한다. */
    private String csrfToken;

    @org.junit.jupiter.api.BeforeEach
    void issueCsrfToken() throws Exception {
        csrfToken = RealCsrf.issue(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        breachedPasswordChecker.reset();
        jdbc.update("DELETE FROM member_oauth_accounts");
        jdbc.update("DELETE FROM members");
    }

    @Test
    void signupCreatesPendingUnverifiedUserWithoutIssuingTokens() throws Exception { // MB-T001
        mockMvc.perform(signup("Tester01", PASSWORD, PASSWORD, "  Tester@Example.COM ", "  테스터  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").isNumber())
                .andExpect(jsonPath("$.accountStatus").value("PENDING"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM members");
        assertThat(row.get("username")).isEqualTo("tester01");
        assertThat(row.get("email")).isEqualTo("tester@example.com");
        assertThat(row.get("nickname")).isEqualTo("테스터");
        assertThat(row.get("account_type")).isEqualTo("LOCAL");
        assertThat(row.get("account_status")).isEqualTo("PENDING");
        assertThat(row.get("email_verified")).isEqualTo(false);
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat((String) row.get("password_hash"))
                .startsWith("pbkdf2-sha256$v1$nfc$i=600000$k=test-key-1$")
                .doesNotContain(PASSWORD);
    }

    @Test
    void duplicateUsernameReturnsConflict() throws Exception { // MB-T002
        mockMvc.perform(signup("tester01", PASSWORD, PASSWORD, "first@example.com", "첫째"))
                .andExpect(status().isCreated());

        mockMvc.perform(signup("TESTER01", PASSWORD, PASSWORD, "second@example.com", "둘째"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MEMBER_USERNAME_DUPLICATE"));

        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception { // MB-T003
        mockMvc.perform(signup("tester01", PASSWORD, PASSWORD, "same@example.com", "첫째"))
                .andExpect(status().isCreated());

        mockMvc.perform(signup("tester02", PASSWORD, PASSWORD, "SAME@example.com", "둘째"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATE"));

        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void concurrentSignupsWithSameUsernameStoreOnlyOne() throws Exception { // MB-T004
        List<Integer> statuses = concurrently(8, i -> signup("racer01", PASSWORD, PASSWORD, "racer" + i + "@example.com", "경쟁"));

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void concurrentSignupsWithSameEmailStoreOnlyOne(CapturedOutput output) throws Exception { // MB-T004
        List<Integer> statuses = concurrently(8, i -> signup("racer0" + i, PASSWORD, PASSWORD, "race@example.com", "경쟁"));

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(memberCount()).isEqualTo(1);
        // DB UNIQUE 위반 상세("Key (email)=(...)")가 로그에 남지 않는다(개인정보·해시 노출 방지)
        assertThat(output.getAll()).doesNotContain("race@example.com");
    }

    @Test
    void invalidFieldsReturnBadRequestWithFieldErrors() throws Exception { // MB-T005
        mockMvc.perform(signup("ab", PASSWORD, PASSWORD, "not-an-email", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'username')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'nickname')]").exists());

        mockMvc.perform(signup("tester01", PASSWORD, PASSWORD, "tester@example.com", "제어\u0007문자"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'nickname')]").exists());

        assertThat(memberCount()).isZero();
    }

    @Test
    void nicknameRejectsUnicodeControlAndFormatCharacters() throws Exception { // MB-T005
        for (String nickname : new String[] {"닉\u0085네임", "닉‮네임", "닉​네임"}) {
            mockMvc.perform(signup("tester01", PASSWORD, PASSWORD, "tester@example.com", nickname))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'nickname')]").exists());
        }
        assertThat(memberCount()).isZero();
    }

    @Test
    void nicknameLengthCountsCodePoints() throws Exception { // MB-T005: 2~20자는 code point 기준
        mockMvc.perform(signup("tester01", PASSWORD, PASSWORD, "tester@example.com", "😀".repeat(20)))
                .andExpect(status().isCreated());
        mockMvc.perform(signup("tester02", PASSWORD, PASSWORD, "tester2@example.com", "😀".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'nickname')]").exists());
    }

    @Test
    void usernameRejectsNonAsciiThatLowercasesToAscii() throws Exception { // MB-T005: U+212A(켈빈 기호) → k 우회 방지
        mockMvc.perform(signup("Kim01", PASSWORD, PASSWORD, "tester@example.com", "테스터"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'username')]").exists());
        assertThat(memberCount()).isZero();
    }

    @Test
    void passwordConfirmMismatchReturnsBadRequest() throws Exception { // MB-T005
        mockMvc.perform(signup("tester01", PASSWORD, PASSWORD + "!", "tester@example.com", "테스터"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'passwordConfirm')]").exists());
    }

    @Test
    void passwordPolicyViolationsReturnBadRequestWithoutEchoingPassword(CapturedOutput output) throws Exception { // MB-T006
        String shortPassword = "short p9!"; // 9자(MB-D026 하한 미만)
        String serviceName = "yhslivestreaming2026";
        String sameAsUsername = "long_username_123";

        for (String[] c : new String[][] {
                {"tester01", shortPassword},
                {"tester01", serviceName},
                {"long_username_123", sameAsUsername}}) {
            mockMvc.perform(signup(c[0], c[1], c[1], "tester@example.com", "테스터"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(c[1]))));
        }

        assertThat(output.getAll()).doesNotContain(shortPassword, serviceName, PASSWORD);
        assertThat(memberCount()).isZero();
    }

    @Test
    void passwordMustBeTenToThirtyCodePoints() throws Exception { // MB-T006, MB-D026: code point 10~30자
        String ten = "abcde fghi";
        String thirty = "correct horse battery staple!!";
        String thirtyOne = "correct horse battery staple!!!";
        assertThat(ten.length()).isEqualTo(10);
        assertThat(thirty.length()).isEqualTo(30);
        assertThat(thirtyOne.length()).isEqualTo(31);

        mockMvc.perform(signup("tester01", ten, ten, "ten@example.com", "테스터"))
                .andExpect(status().isCreated());
        mockMvc.perform(signup("tester02", thirty, thirty, "thirty@example.com", "테스터"))
                .andExpect(status().isCreated());
        mockMvc.perform(signup("tester03", thirtyOne, thirtyOne, "long@example.com", "테스터"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message").value("비밀번호는 30자 이하여야 합니다."));
        mockMvc.perform(signup("tester04", "abcd fghi", "abcd fghi", "short@example.com", "테스터"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message").value("비밀번호는 10자 이상이어야 합니다."));

        assertThat(memberCount()).isEqualTo(2);
    }

    @Test
    void breachedPasswordReturnsBadRequestWithoutStoring(CapturedOutput output) throws Exception { // MB-T017
        breachedPasswordChecker.willReturn(Result.BREACHED);

        mockMvc.perform(signup("tester01", PASSWORD, PASSWORD, "tester@example.com", "테스터"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_PASSWORD_BREACHED"))
                .andExpect(jsonPath("$.detail").value("유출된 적이 있는 비밀번호라 쓸 수 없습니다. 다른 비밀번호를 입력해 주세요."))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(PASSWORD))));

        assertThat(memberCount()).isZero();
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void unavailablePasswordCheckReturnsServiceUnavailableWithoutStoring(CapturedOutput output) throws Exception { // MB-T017
        breachedPasswordChecker.willReturn(Result.UNAVAILABLE);

        mockMvc.perform(signup("tester01", PASSWORD, PASSWORD, "tester@example.com", "테스터"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEMBER_PASSWORD_CHECK_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("지금은 비밀번호 안전 여부를 확인할 수 없어 가입할 수 없습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(PASSWORD))));

        assertThat(memberCount()).isZero();
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    private org.springframework.test.web.servlet.RequestBuilder signup(
            String username, String password, String passwordConfirm, String email, String nickname) {
        String body = """
                {"username": %s, "password": %s, "passwordConfirm": %s, "email": %s, "nickname": %s}
                """.formatted(json(username), json(password), json(passwordConfirm), json(email), json(nickname));
        // CSRF 보호(G-D014)는 CsrfIntegrationTest가 검증한다. 여기서는 발급 API의 실제 토큰을 붙인다.
        // IP 요청 제한(MB-D018)은 LoginApiIntegrationTest가 검증한다. 여기서는 요청마다 다른 IP를 써서 가입 규칙만 본다.
        String clientIp = "198.18.0." + (NEXT_IP.incrementAndGet() % 250 + 1);
        return post("/api/v1/auth/signup").with(RealCsrf.token(csrfToken)).with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\u0007", "\\u0007") + "\"";
    }

    private long memberCount() {
        return jdbc.queryForObject("SELECT count(*) FROM members", Long.class);
    }

    private List<Integer> concurrently(int threads, java.util.function.IntFunction<
            org.springframework.test.web.servlet.RequestBuilder> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Integer> task = () -> {
                    start.await();
                    return mockMvc.perform(request.apply(index)).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
