package com.yhs.livestreaming.member.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.persistence.GlobalJpaConfig;
import com.yhs.livestreaming.member.app.oauth.GoogleLinkConflictException;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * MB-T047(영속성): GOOGLE 회원·OAuth 연결 행을 실제 PostgreSQL(Testcontainers)에서 같은 트랜잭션으로 만들고, provider·sub
 * UNIQUE 충돌만 동시 가입으로 구분하며, 만료 PENDING 재생성·OTP 활성화·이메일 갱신의 조건과 경합을 확인한다(MB-D025).
 * 각 메서드가 자기 트랜잭션을 쓰므로 테스트 트랜잭션은 끈다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GoogleAccountRepositoryAdapter.class, MemberRepositoryAdapter.class, GlobalJpaConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class GoogleAccountRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final String SUB = "google-sub-1234567890";

    @Autowired
    private GoogleAccountRepositoryAdapter repository;

    @Autowired
    private MemberRepositoryAdapter members;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM member_oauth_accounts");
        jdbc.update("DELETE FROM members");
    }

    @Test
    void createsPendingGoogleMemberAndLinkTogether() {
        Member created = repository.create(Member.registerGoogle("g@example.com", "구글사용자"), SUB, "g@example.com");

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM members WHERE id = ?", created.getId());
        assertThat(row.get("account_type")).isEqualTo("GOOGLE");
        assertThat(row.get("account_status")).isEqualTo("PENDING");
        assertThat(row.get("email_verified")).isEqualTo(false);
        assertThat(row.get("username")).isNull();
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("role")).isEqualTo("USER");
        Map<String, Object> link = jdbc.queryForMap("SELECT * FROM member_oauth_accounts");
        assertThat(link).containsEntry("member_id", created.getId()).containsEntry("provider", "GOOGLE")
                .containsEntry("provider_user_id", SUB).containsEntry("email", "g@example.com");
        assertThat(link.get("created_at")).isNotNull();
        assertThat(repository.findBySub(SUB)).hasValueSatisfying(m -> assertThat(m.getId()).isEqualTo(created.getId()));
        assertThat(repository.findBySub("other")).isEmpty();
    }

    @Test
    void sameEmailAsLocalMemberIsASeparateMember() {
        members.save(Member.registerLocal("tester01", "hash", "same@example.com", "로컬"));

        Member google = repository.create(Member.registerGoogle("same@example.com", "구글"), SUB, "same@example.com");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE email = 'same@example.com'", Long.class))
                .isEqualTo(2L);
        assertThat(google.getAccountType()).isEqualTo(AccountType.GOOGLE);
    }

    @Test
    void duplicateSubIsALinkConflictAndLeavesNoOrphanMember() {
        repository.create(Member.registerGoogle("g@example.com", "첫번째"), SUB, "g@example.com");

        assertThatThrownBy(() -> repository.create(Member.registerGoogle("g@example.com", "두번째"), SUB,
                "g@example.com")).isInstanceOf(GoogleLinkConflictException.class);

        assertThat(count("members")).isEqualTo(1L); // 실패한 트랜잭션의 회원 행은 롤백됐다
        assertThat(count("member_oauth_accounts")).isEqualTo(1L);
    }

    @Test
    void concurrentFirstLoginsCreateExactlyOneMemberWithoutOrphans() throws Exception {
        List<Object> results = runConcurrently(8, i -> {
            try {
                return repository.create(Member.registerGoogle("g@example.com", "사용자" + i), SUB, "g@example.com");
            } catch (GoogleLinkConflictException ex) {
                return ex;
            }
        });

        assertThat(results).filteredOn(Member.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(GoogleLinkConflictException.class::isInstance).hasSize(7);
        assertThat(count("members")).isEqualTo(1L);
        assertThat(count("member_oauth_accounts")).isEqualTo(1L);
    }

    @Test
    void expiredPendingIsReplacedByANewPendingMemberInOneTransaction() {
        Member old = repository.create(Member.registerGoogle("old@example.com", "옛사람"), SUB, "old@example.com");
        ageMember(old.getId(), Duration.ofHours(25));
        Instant now = Instant.now();

        Optional<Member> replaced = repository.replaceExpiredPending(old.getId(), SUB, now.minus(Member.PENDING_TTL),
                Member.registerGoogle("new@example.com", "새사람"), "new@example.com");

        assertThat(replaced).hasValueSatisfying(m -> {
            assertThat(m.getId()).isNotEqualTo(old.getId());
            assertThat(m.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE id = ?", Long.class, old.getId()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT member_id FROM member_oauth_accounts WHERE provider_user_id = ?",
                Long.class, SUB)).isEqualTo(replaced.get().getId());
        assertThat(count("members")).isEqualTo(1L);
    }

    @Test
    void replacementIsRefusedAndRolledBackUnlessTheMemberIsAnExpiredPendingGoogleLinkedToTheSub() {
        Instant expiredBefore = Instant.now().minus(Member.PENDING_TTL);
        Member fresh = repository.create(Member.registerGoogle("a@example.com", "아직유효"), SUB, "a@example.com");

        assertThat(repository.replaceExpiredPending(fresh.getId(), SUB, expiredBefore,
                Member.registerGoogle("x@example.com", "새사람"), "x@example.com")).as("만료 전").isEmpty();

        ageMember(fresh.getId(), Duration.ofHours(25));
        assertThat(repository.replaceExpiredPending(fresh.getId(), "another-sub", expiredBefore,
                Member.registerGoogle("x@example.com", "새사람"), "x@example.com")).as("다른 sub").isEmpty();

        jdbc.update("UPDATE members SET account_status = 'ACTIVE' WHERE id = ?", fresh.getId());
        assertThat(repository.replaceExpiredPending(fresh.getId(), SUB, expiredBefore,
                Member.registerGoogle("x@example.com", "새사람"), "x@example.com")).as("ACTIVE").isEmpty();

        // 실패한 시도는 모두 롤백: 원래 회원·연결 그대로, 새 회원 없음
        assertThat(count("members")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT member_id FROM member_oauth_accounts", Long.class))
                .isEqualTo(fresh.getId());
    }

    @Test
    void concurrentReplacementsOfAnExpiredMemberCreateExactlyOneNewMember() throws Exception {
        Member old = repository.create(Member.registerGoogle("old@example.com", "옛사람"), SUB, "old@example.com");
        ageMember(old.getId(), Duration.ofHours(25));
        Instant expiredBefore = Instant.now().minus(Member.PENDING_TTL);

        List<Object> results = runConcurrently(6, i -> repository.replaceExpiredPending(old.getId(), SUB,
                expiredBefore, Member.registerGoogle("new@example.com", "새사람" + i), "new@example.com"));

        assertThat(results).filteredOn(r -> r instanceof Optional<?> o && o.isPresent()).hasSize(1);
        assertThat(count("members")).isEqualTo(1L);
        assertThat(count("member_oauth_accounts")).isEqualTo(1L);
    }

    @Test
    void activationSetsActiveVerifiedAndTheConfirmedEmailOnMemberAndLink() {
        Member pending = repository.create(Member.registerGoogle("old@example.com", "사용자"), SUB, "old@example.com");
        Instant now = Instant.now();

        int rows = repository.activatePending(pending.getId(), "new@example.com", now.minus(Member.PENDING_TTL), now);

        assertThat(rows).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM members WHERE id = ?", pending.getId());
        assertThat(row).containsEntry("account_status", "ACTIVE").containsEntry("email_verified", true)
                .containsEntry("email", "new@example.com");
        assertThat(jdbc.queryForObject("SELECT email FROM member_oauth_accounts", String.class))
                .isEqualTo("new@example.com");
    }

    @Test
    void activationRefusesExpiredPendingAndLocalMembers() {
        Member pending = repository.create(Member.registerGoogle("old@example.com", "사용자"), SUB, "old@example.com");
        ageMember(pending.getId(), Duration.ofHours(25));
        Member local = members.save(Member.registerLocal("tester01", "hash", "l@example.com", "로컬"));
        Instant now = Instant.now();

        assertThat(repository.activatePending(pending.getId(), "new@example.com", now.minus(Member.PENDING_TTL), now))
                .isZero();
        assertThat(repository.activatePending(local.getId(), "new@example.com", now.minus(Member.PENDING_TTL), now))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT email FROM members WHERE id = ?", String.class, pending.getId()))
                .isEqualTo("old@example.com");
        assertThat(jdbc.queryForObject("SELECT email FROM members WHERE id = ?", String.class, local.getId()))
                .isEqualTo("l@example.com");
    }

    @Test
    void activeGoogleMemberEmailIsUpdatedOnMemberAndLinkButNotForOtherStates() {
        Member google = repository.create(Member.registerGoogle("old@example.com", "사용자"), SUB, "old@example.com");
        Instant now = Instant.now();
        assertThat(repository.updateActiveEmail(google.getId(), "new@example.com", now)).as("PENDING").isZero();

        jdbc.update("UPDATE members SET account_status = 'ACTIVE', email_verified = true WHERE id = ?", google.getId());
        assertThat(repository.updateActiveEmail(google.getId(), "new@example.com", now)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email FROM members WHERE id = ?", String.class, google.getId()))
                .isEqualTo("new@example.com");
        assertThat(jdbc.queryForObject("SELECT email FROM member_oauth_accounts", String.class))
                .isEqualTo("new@example.com");

        jdbc.update("UPDATE members SET account_status = 'DISABLED' WHERE id = ?", google.getId());
        assertThat(repository.updateActiveEmail(google.getId(), "x@example.com", now)).as("DISABLED").isZero();
    }

    @Test
    void activationAndEmailUpdateRollBackWhenTheLinkRowIsMissing() { // 회원·연결 행은 함께 바뀌거나 둘 다 그대로
        Member pending = repository.create(Member.registerGoogle("old@example.com", "사용자"), SUB, "old@example.com");
        jdbc.update("DELETE FROM member_oauth_accounts");
        Instant now = Instant.now();

        assertThat(repository.activatePending(pending.getId(), "new@example.com", now.minus(Member.PENDING_TTL), now))
                .isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM members WHERE id = ?", pending.getId()))
                .containsEntry("account_status", "PENDING").containsEntry("email", "old@example.com");

        jdbc.update("UPDATE members SET account_status = 'ACTIVE', email_verified = true WHERE id = ?", pending.getId());
        assertThat(repository.updateActiveEmail(pending.getId(), "new@example.com", now)).isZero();
        assertThat(jdbc.queryForObject("SELECT email FROM members WHERE id = ?", String.class, pending.getId()))
                .isEqualTo("old@example.com");
    }

    @Test
    void activationAndReplacementOfAnExpiredMemberNeverBothSucceed() throws Exception {
        Member old = repository.create(Member.registerGoogle("old@example.com", "옛사람"), SUB, "old@example.com");
        ageMember(old.getId(), Duration.ofHours(25));
        Instant now = Instant.now();

        List<Object> results = runConcurrently(2, i -> i == 0
                ? repository.activatePending(old.getId(), "old@example.com", now.minus(Member.PENDING_TTL), now)
                : repository.replaceExpiredPending(old.getId(), SUB, now.minus(Member.PENDING_TTL),
                        Member.registerGoogle("new@example.com", "새사람"), "new@example.com"));

        assertThat(results.get(0)).isEqualTo(0); // 만료 PENDING은 활성화되지 않는다
        assertThat(count("members")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT account_status FROM members", String.class)).isEqualTo("PENDING");
    }

    private void ageMember(long id, Duration age) {
        jdbc.update("UPDATE members SET created_at = now() - (? * interval '1 second') WHERE id = ?",
                age.toSeconds(), id);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static List<Object> runConcurrently(int threads, java.util.function.IntFunction<Object> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return task.apply(index);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
