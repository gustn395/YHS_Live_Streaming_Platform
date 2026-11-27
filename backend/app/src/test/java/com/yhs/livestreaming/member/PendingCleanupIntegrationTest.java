package com.yhs.livestreaming.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.member.app.cleanup.CleanupResult;
import com.yhs.livestreaming.member.app.cleanup.PendingMemberCleanupService;
import com.yhs.livestreaming.member.in.PendingMemberCleanupScheduler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import testsupport.AppIntegrationTest;
import testsupport.RealCsrf;

/**
 * MB-T069: 만료 PENDING 정리·LOCAL 가입 시 즉시 회수를 실제 보안 체인·PostgreSQL로 확인한다(MB-D030). 테스트 프로필은 배치
 * 자동 실행을 끄고(member.cleanup.enabled=false), 정리 서비스는 직접 호출한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
class PendingCleanupIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PendingMemberCleanupService cleanup;

    @Autowired
    private ApplicationContext context;

    private String csrfToken;
    private int ip;

    @BeforeEach
    void issueCsrf() throws Exception {
        csrfToken = RealCsrf.issue(mockMvc);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM member_oauth_accounts");
        jdbc.update("DELETE FROM members");
        redis.delete(redis.keys("*"));
    }

    @Test
    void schedulerIsOffInTestsButTheServiceCanBeCalled() {
        assertThat(context.getBeansOfType(PendingMemberCleanupScheduler.class)).isEmpty();
        assertThat(cleanup.cleanupExpired().completed()).isTrue();
    }

    @Test
    void signupReclaimsUsernameAndEmailHeldByExpiredPendingMembers() throws Exception {
        long byName = signupAndExpire("taken001", "old1@example.com");
        long byEmail = signupAndExpire("old00002", "taken@example.com");

        signup("taken001", "new1@example.com").andExpect(status().isCreated());
        signup("new00002", "taken@example.com").andExpect(status().isCreated());

        assertThat(ids()).doesNotContain(byName, byEmail).hasSize(2);
    }

    @Test
    void validPendingStillBlocksTheSameUsernameAndEmail() throws Exception {
        signup("valid001", "valid@example.com").andExpect(status().isCreated());

        signup("valid001", "other@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_USERNAME_DUPLICATE"));
        signup("other001", "valid@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATE"));
    }

    @Test
    void expiredMemberCannotLogInAndTheNameIsReusableAfterCleanup() throws Exception {
        long expired = signupAndExpire("expired01", "e@example.com");
        mockMvc.perform(post("/api/v1/auth/login").with(RealCsrf.token(csrfToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"expired01\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        CleanupResult result = cleanup.cleanupExpired();

        assertThat(result.localDeleted()).isEqualTo(1);
        assertThat(ids()).doesNotContain(expired);
        signup("expired01", "e@example.com").andExpect(status().isCreated());
    }

    @Test
    void cleanupRemovesExpiredLocalAndGoogleButKeepsTheRest() throws Exception {
        long expiredLocal = signupAndExpire("expired01", "e@example.com");
        long validLocal = idOf(signup("valid001", "v@example.com").andExpect(status().isCreated()));
        long expiredGoogle = insertGoogle("sub-expired", "g@example.com", "PENDING", 25);
        long activeGoogle = insertGoogle("sub-active", "a@example.com", "ACTIVE", 25);

        CleanupResult result = cleanup.cleanupExpired();

        assertThat(result.localDeleted()).isEqualTo(1);
        assertThat(result.googleDeleted()).isEqualTo(1);
        assertThat(ids()).containsExactlyInAnyOrder(validLocal, activeGoogle).doesNotContain(expiredLocal, expiredGoogle);
        assertThat(jdbc.queryForList("SELECT provider_user_id FROM member_oauth_accounts", String.class))
                .containsExactly("sub-active");
    }

    // ----- 도우미 -----

    private ResultActions signup(String username, String email) throws Exception {
        String body = """
                {"username":"%s","password":"%s","passwordConfirm":"%s","email":"%s","nickname":"테스터"}
                """.formatted(username, PASSWORD, PASSWORD, email);
        String clientIp = "198.18.1." + (++ip); // 가입 IP 제한(MB-D027)과 섞이지 않게 요청마다 다른 IP
        return mockMvc.perform(post("/api/v1/auth/signup").with(RealCsrf.token(csrfToken)).with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long signupAndExpire(String username, String email) throws Exception {
        long id = idOf(signup(username, email).andExpect(status().isCreated()));
        jdbc.update("UPDATE members SET created_at = now() - INTERVAL '25 hours' WHERE id = ?", id);
        return id;
    }

    private long insertGoogle(String sub, String email, String status, int hoursAgo) {
        Long id = jdbc.queryForObject("INSERT INTO members (email, email_verified, nickname, role, account_type, "
                + "account_status, created_at, updated_at) VALUES (?, false, '구글', 'USER', 'GOOGLE', ?, "
                + "now() - make_interval(hours => ?), now()) RETURNING id", Long.class, email, status, hoursAgo);
        jdbc.update("INSERT INTO member_oauth_accounts (member_id, provider, provider_user_id, email, created_at) "
                + "VALUES (?, 'GOOGLE', ?, ?, now())", id, sub, email);
        return id;
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Long.parseLong(body.replaceAll(".*\"memberId\":(\\d+).*", "$1"));
    }

    private List<Long> ids() {
        return jdbc.queryForList("SELECT id FROM members", Long.class);
    }
}
