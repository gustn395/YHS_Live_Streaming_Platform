package com.yhs.livestreaming.member.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.persistence.GlobalJpaConfig;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.cleanup.CleanupResult;
import com.yhs.livestreaming.member.app.cleanup.ExpiredPendingMemberStore.CleanupBatch;
import com.yhs.livestreaming.member.app.cleanup.MemberCleanupProperties;
import com.yhs.livestreaming.member.app.cleanup.PendingMemberCleanupService;
import com.yhs.livestreaming.member.domain.Member;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * MB-T061·MB-T065~MB-T068(영속성·동시성): 만료 PENDING 정리 배치와 LOCAL 가입 시 즉시 회수를 실제 PostgreSQL에서 확인한다(MB-D030,
 * MB-D025). 각 메서드가 자기 트랜잭션을 쓰므로 테스트 트랜잭션은 끈다. 경합은 CyclicBarrier로 맞추고 Thread.sleep은 쓰지 않는다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ExpiredPendingMemberStoreAdapter.class, MemberRepositoryAdapter.class, GoogleAccountRepositoryAdapter.class,
        GlobalJpaConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class ExpiredPendingCleanupPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final int RACES = 5;

    @Autowired
    private ExpiredPendingMemberStoreAdapter store;

    @Autowired
    private MemberRepositoryAdapter members;

    @Autowired
    private GoogleAccountRepositoryAdapter google;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberJpaRepository memberJpa;

    @Autowired
    private OAuthAccountJpaRepository linkJpa;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM member_oauth_accounts");
        jdbc.update("DELETE FROM members");
    }

    // ----- 배치 -----

    @Test
    void localBatchDeletesOnlyExpiredPendingLocalMembers() { // MB-T065
        long expired1 = local("expired01", "e1@example.com", hoursAgo(25), "PENDING");
        long expired2 = local("expired02", "e2@example.com", hoursAgo(25), "PENDING");
        long valid = local("valid001", "v@example.com", hoursAgo(23), "PENDING");
        long active = local("active01", "a@example.com", hoursAgo(25), "ACTIVE");
        long disabled = local("disabl01", "d@example.com", hoursAgo(25), "DISABLED");
        long googleExpired = google("sub-g1", "g1@example.com", hoursAgo(25), "PENDING");

        CleanupBatch batch = store.deleteExpiredLocal(cutoff(), 0L, 500);

        assertThat(batch.scanned()).isEqualTo(2);
        assertThat(batch.deleted()).isEqualTo(2);
        assertThat(batch.lastId()).isEqualTo(Math.max(expired1, expired2));
        assertThat(ids()).containsExactlyInAnyOrder(valid, active, disabled, googleExpired);
    }

    @Test
    void localBatchUsesTheIdCursorAndLimit() { // MB-T065: OFFSET 없이 ID 순서 커서
        long first = local("expired01", "e1@example.com", hoursAgo(25), "PENDING");
        long second = local("expired02", "e2@example.com", hoursAgo(25), "PENDING");
        long third = local("expired03", "e3@example.com", hoursAgo(25), "PENDING");

        CleanupBatch one = store.deleteExpiredLocal(cutoff(), 0L, 2);
        CleanupBatch two = store.deleteExpiredLocal(cutoff(), one.lastId(), 2);

        assertThat(one).isEqualTo(new CleanupBatch(2, second, 2));
        assertThat(two).isEqualTo(new CleanupBatch(1, third, 1));
        assertThat(ids()).isEmpty();
        assertThat(first).isLessThan(second);
    }

    @Test
    void exactly24HoursIsExpired() { // MB-T065: 정확히 24시간 지난 순간부터 만료
        Instant cutoff = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(Duration.ofHours(24));
        long atCutoff = local("boundary", "b@example.com", cutoff, "PENDING");
        long justAfter = local("justafter", "j@example.com", cutoff.plusMillis(1), "PENDING");

        store.deleteExpiredLocal(cutoff, 0L, 500);

        assertThat(ids()).containsExactly(justAfter).doesNotContain(atCutoff);
    }

    @Test
    void googleBatchDeletesLinksThenMembersAndKeepsOthers() { // MB-T066
        long expired = google("sub-1", "g1@example.com", hoursAgo(25), "PENDING");
        long twoLinks = google("sub-2", "g2@example.com", hoursAgo(25), "PENDING");
        jdbc.update("INSERT INTO member_oauth_accounts (member_id, provider, provider_user_id, email, created_at) "
                + "VALUES (?, 'GOOGLE', 'sub-2b', NULL, now())", twoLinks); // 스키마상 회원당 연결 행은 하나로 보장되지 않는다
        long orphan = members.save(Member.registerGoogle("g3@example.com", "고아")).getId();
        setCreated(orphan, hoursAgo(25));
        long valid = google("sub-4", "g4@example.com", hoursAgo(23), "PENDING");
        long active = google("sub-5", "g5@example.com", hoursAgo(25), "ACTIVE");
        long localExpired = local("expired01", "e1@example.com", hoursAgo(25), "PENDING");

        CleanupBatch batch = store.deleteExpiredGoogle(cutoff(), 0L, 500);

        assertThat(batch.scanned()).isEqualTo(3);
        assertThat(batch.deleted()).isEqualTo(3); // 연결 행 수(3)가 아니라 회원 ID 기준
        assertThat(ids()).containsExactlyInAnyOrder(valid, active, localExpired).doesNotContain(expired, orphan);
        assertThat(jdbc.queryForList("SELECT provider_user_id FROM member_oauth_accounts", String.class))
                .containsExactlyInAnyOrder("sub-4", "sub-5");
    }

    // ----- LOCAL 가입 시 즉시 회수 -----

    @Test
    void expiredPendingHoldersAreNotTakenButOthersAre() { // MB-T067
        local("expired01", "expired@example.com", hoursAgo(25), "PENDING");
        local("valid001", "valid@example.com", hoursAgo(23), "PENDING");
        local("active01", "active@example.com", hoursAgo(25), "ACTIVE");
        google("sub-1", "google@example.com", hoursAgo(1), "ACTIVE");

        assertThat(members.isUsernameTaken("expired01", cutoff())).isFalse();
        assertThat(members.isLocalEmailTaken("expired@example.com", cutoff())).isFalse();
        assertThat(members.isUsernameTaken("valid001", cutoff())).isTrue();
        assertThat(members.isLocalEmailTaken("active@example.com", cutoff())).isTrue();
        assertThat(members.isLocalEmailTaken("google@example.com", cutoff())).isFalse(); // GOOGLE과 같은 이메일 허용
    }

    @Test
    void reclaimsWhenOnlyTheUsernameOrOnlyTheEmailOrBothCollide() { // MB-T067
        long byName = local("taken001", "old1@example.com", hoursAgo(25), "PENDING");
        members.saveReclaimingExpired(newLocal("taken001", "new1@example.com"), cutoff());
        long byEmail = local("old00002", "taken@example.com", hoursAgo(25), "PENDING");
        members.saveReclaimingExpired(newLocal("new00002", "taken@example.com"), cutoff());
        long both = local("same0003", "same@example.com", hoursAgo(25), "PENDING");
        members.saveReclaimingExpired(newLocal("same0003", "same@example.com"), cutoff());

        assertThat(ids()).doesNotContain(byName, byEmail, both).hasSize(3);
        assertThat(usernames()).containsExactlyInAnyOrder("taken001", "new00002", "same0003");
    }

    @Test
    void reclaimsTwoDifferentExpiredMembersAtOnce() { // MB-T067
        long holdsName = local("taken001", "a@example.com", hoursAgo(25), "PENDING");
        long holdsEmail = local("other001", "taken@example.com", hoursAgo(26), "PENDING");

        Member saved = members.saveReclaimingExpired(newLocal("taken001", "taken@example.com"), cutoff());

        assertThat(ids()).containsExactly(saved.getId()).doesNotContain(holdsName, holdsEmail);
    }

    @Test
    void conflictWithANonReclaimableMemberRollsBackTheReclaim() { // MB-T067: 저장 실패 시 삭제도 롤백
        long expired = local("taken001", "a@example.com", hoursAgo(25), "PENDING");
        long valid = local("valid001", "taken@example.com", hoursAgo(23), "PENDING");

        assertThatThrownBy(() -> members.saveReclaimingExpired(newLocal("taken001", "taken@example.com"), cutoff()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.EMAIL_DUPLICATE));
        assertThat(ids()).containsExactlyInAnyOrder(expired, valid);
    }

    @Test
    void otherDatabaseErrorsAreNotReportedAsDuplicatesAndRollBack() { // MB-T067
        long expired = local("taken001", "a@example.com", hoursAgo(25), "PENDING");
        Member tooLong = Member.registerLocal("taken001", "pbkdf2-sha256$test-hash", "new@example.com", "가".repeat(51));

        assertThatThrownBy(() -> members.saveReclaimingExpired(tooLong, cutoff()))
                .isNotInstanceOf(BusinessException.class);
        assertThat(ids()).containsExactly(expired);
    }

    @Test
    void googleMemberWithTheSameEmailIsNotReclaimedByLocalSignup() { // MB-T067
        long googleExpired = google("sub-1", "same@example.com", hoursAgo(25), "PENDING");

        Member saved = members.saveReclaimingExpired(newLocal("local001", "same@example.com"), cutoff());

        assertThat(ids()).containsExactlyInAnyOrder(googleExpired, saved.getId());
    }

    // ----- 동시성(MB-T068) -----

    @Test
    void batchAndLocalReclaimOfTheSameMemberBothSucceed() throws Exception {
        for (int i = 0; i < RACES; i++) {
            long old = local("racer001", "old@example.com", hoursAgo(25), "PENDING");
            Instant cutoff = cutoff();

            List<Object> results = race(() -> store.deleteExpiredLocal(cutoff, 0L, 500),
                    () -> members.saveReclaimingExpired(newLocal("racer001", "new@example.com"), cutoff));

            assertThat(results).noneMatch(Throwable.class::isInstance);
            assertThat(ids()).hasSize(1).doesNotContain(old);
            assertThat(usernames()).containsExactly("racer001");
            assertThat(jdbc.queryForObject("SELECT email FROM members", String.class)).isEqualTo("new@example.com");
            clean();
        }
    }

    @Test
    void batchAndGoogleRecreationLeaveNoOrphansOrDuplicates() throws Exception {
        for (int i = 0; i < RACES; i++) {
            long old = google("sub-race", "g@example.com", hoursAgo(25), "PENDING");
            Instant cutoff = cutoff();

            List<Object> results = race(() -> store.deleteExpiredGoogle(cutoff, 0L, 500),
                    () -> google.replaceExpiredPending(old, "sub-race", cutoff,
                            Member.registerGoogle("g@example.com", "새구글"), "g@example.com"));

            assertThat(results).noneMatch(Throwable.class::isInstance);
            assertThat(ids()).doesNotContain(old);
            int links = jdbc.queryForObject(
                    "SELECT count(*) FROM member_oauth_accounts WHERE provider_user_id = 'sub-race'", Integer.class);
            Optional<?> replaced = (Optional<?>) results.get(1);
            assertThat(links).isEqualTo(replaced.isPresent() ? 1 : 0);
            assertNoOrphans();
            clean();
        }
    }

    @Test
    void batchAndActivationNeverTouchTheSameMemberAtTheBoundary() throws Exception {
        // 같은 기준 시각에서 정리는 created_at <= 기준, 활성화는 created_at > 기준만 바꾼다. 경계 양쪽 회원으로 동시에 돌려도
        // 순서와 관계없이 결과가 정해져 있어야 한다(배치의 조건 재확인·잠금 순서가 깨지면 이 결과가 흔들린다).
        for (int i = 0; i < RACES; i++) {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Instant cutoff = now.minus(Duration.ofHours(24));
            long localAtCutoff = local("atcut001", "a1@example.com", cutoff, "PENDING");
            long localAfter = local("after001", "a2@example.com", cutoff.plusMillis(1), "PENDING");
            long googleAtCutoff = google("sub-at", "g1@example.com", cutoff, "PENDING");
            long googleAfter = google("sub-after", "g2@example.com", cutoff.plusMillis(1), "PENDING");

            List<Object> results = race(() -> store.deleteExpiredLocal(cutoff, 0L, 500),
                    () -> members.activatePending(localAtCutoff, cutoff, now),
                    () -> members.activatePending(localAfter, cutoff, now),
                    () -> store.deleteExpiredGoogle(cutoff, 0L, 500),
                    () -> google.activatePending(googleAtCutoff, "g1@example.com", cutoff, now),
                    () -> google.activatePending(googleAfter, "g2@example.com", cutoff, now));

            assertThat(results).noneMatch(Throwable.class::isInstance);
            assertThat(results.get(1)).isEqualTo(0); // 만료 회원은 활성화되지 않는다
            assertThat(results.get(2)).isEqualTo(1);
            assertThat(results.get(4)).isEqualTo(0);
            assertThat(results.get(5)).isEqualTo(1);
            assertThat(ids()).containsExactlyInAnyOrder(localAfter, googleAfter);
            assertThat(jdbc.queryForList("SELECT account_status FROM members", String.class))
                    .containsExactly("ACTIVE", "ACTIVE");
            assertThat(jdbc.queryForList("SELECT provider_user_id FROM member_oauth_accounts", String.class))
                    .containsExactly("sub-after");
            assertNoOrphans();
            clean();
        }
    }

    @Test
    void serviceDrainsEveryBatchAgainstTheDatabase() { // MB-T061·MB-T065: 실제 DB에서 배치 여러 번
        for (int i = 1; i <= 5; i++) {
            local("expire0" + i, "e" + i + "@example.com", hoursAgo(25), "PENDING");
        }
        for (int i = 1; i <= 3; i++) {
            google("sub-e" + i, "g" + i + "@example.com", hoursAgo(25), "PENDING");
        }
        long valid = local("valid001", "v@example.com", hoursAgo(23), "PENDING");
        PendingMemberCleanupService service = new PendingMemberCleanupService(store, Clock.systemUTC(),
                new MemberCleanupProperties(true, Duration.ofHours(1), 2));

        CleanupResult result = service.cleanupExpired();

        assertThat(result.localDeleted()).isEqualTo(5);
        assertThat(result.googleDeleted()).isEqualTo(3);
        assertThat(result.completed()).isTrue();
        assertThat(ids()).containsExactly(valid);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_oauth_accounts", Integer.class)).isZero();
    }

    @Test
    void googleBatchRollsBackLinkDeletionWhenATargetMemberSurvives() { // MB-T066: 연결 행만 지운 상태를 커밋하지 않음
        long expired = google("sub-1", "g1@example.com", hoursAgo(25), "PENDING");
        // 회원 행 삭제가 0행이 되는 상황(그 사이 상태가 바뀜)을 결정적으로 만들려고 그 한 메서드만 바꾼 저장소를 쓴다
        MemberJpaRepository surviving = (MemberJpaRepository) Proxy.newProxyInstance(
                MemberJpaRepository.class.getClassLoader(), new Class<?>[] {MemberJpaRepository.class},
                (proxy, method, args) -> method.getName().equals("deleteExpiredPendingIn") ? 0
                        : invoke(method, memberJpa, args));
        ExpiredPendingMemberStoreAdapter adapter =
                new ExpiredPendingMemberStoreAdapter(surviving, linkJpa, transactionManager);

        assertThatThrownBy(() -> adapter.deleteExpiredGoogle(cutoff(), 0L, 500))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ids()).containsExactly(expired);
        assertThat(jdbc.queryForList("SELECT provider_user_id FROM member_oauth_accounts", String.class))
                .containsExactly("sub-1"); // 연결 행 삭제도 롤백됐다
    }

    @Test
    void concurrentSignupsReclaimingTheSameMemberLetOnlyOneWin() throws Exception {
        for (int i = 0; i < RACES; i++) {
            long old = local("racer001", "old@example.com", hoursAgo(25), "PENDING");
            Instant cutoff = cutoff();

            List<Object> results = race(
                    () -> members.saveReclaimingExpired(newLocal("racer001", "first@example.com"), cutoff),
                    () -> members.saveReclaimingExpired(newLocal("racer001", "second@example.com"), cutoff));

            assertThat(results).filteredOn(Member.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(BusinessException.class::isInstance).singleElement()
                    .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(MemberErrorCode.USERNAME_DUPLICATE));
            assertThat(ids()).hasSize(1).doesNotContain(old);
            clean();
        }
    }

    // ----- 도우미 -----

    /** 모든 작업이 장벽에서 함께 출발하게 하고, 결과 또는 던진 예외를 순서대로 돌려준다. */
    @SafeVarargs
    private List<Object> race(Callable<?>... tasks) throws Exception {
        CyclicBarrier start = new CyclicBarrier(tasks.length);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return task.call();
                    } catch (Throwable ex) {
                        return ex;
                    }
                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private void assertNoOrphans() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members m WHERE m.account_type = 'GOOGLE' AND NOT EXISTS "
                + "(SELECT 1 FROM member_oauth_accounts a WHERE a.member_id = m.id)", Integer.class)).isZero();
    }

    private long local(String username, String email, Instant createdAt, String status) {
        long id = members.save(newLocal(username, email)).getId();
        setCreated(id, createdAt);
        jdbc.update("UPDATE members SET account_status = ? WHERE id = ?", status, id);
        return id;
    }

    private long google(String sub, String email, Instant createdAt, String status) {
        long id = google.create(Member.registerGoogle(email, "구글"), sub, email).getId();
        setCreated(id, createdAt);
        jdbc.update("UPDATE members SET account_status = ? WHERE id = ?", status, id);
        return id;
    }

    private void setCreated(long id, Instant createdAt) {
        jdbc.update("UPDATE members SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), id);
    }

    private static Member newLocal(String username, String email) {
        return Member.registerLocal(username, "pbkdf2-sha256$test-hash", email, "닉네임");
    }

    private static Instant hoursAgo(int hours) {
        return Instant.now().minus(Duration.ofHours(hours));
    }

    private static Instant cutoff() {
        return Instant.now().minus(Duration.ofHours(24));
    }

    private List<Long> ids() {
        return jdbc.queryForList("SELECT id FROM members", Long.class);
    }

    private List<String> usernames() {
        return jdbc.queryForList("SELECT username FROM members WHERE username IS NOT NULL", String.class);
    }
}
