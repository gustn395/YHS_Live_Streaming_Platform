package com.yhs.livestreaming.member.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.persistence.GlobalJpaConfig;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * MB-T010·MB-T002·MB-T003: 실제 PostgreSQL(Testcontainers)에 Flyway 마이그레이션을 적용하고 스키마 제약과 409 변환을 확인한다
 * (G-D009, MB-D008). Hibernate는 ddl-auto=validate라 엔티티와 DDL이 다르면 컨텍스트가 뜨지 않는다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MemberRepositoryAdapter.class, GlobalJpaConfig.class})
@Testcontainers
class MemberRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MemberRepositoryAdapter repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesLocalMemberWithTimestamps() {
        Member saved = repository.save(local("tester01", "tester@example.com"));

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(repository.existsByUsername("tester01")).isTrue();
        assertThat(repository.existsLocalEmail("tester@example.com")).isTrue();
    }

    @Test
    void duplicateUsernameIsConvertedToUsernameConflict() {
        repository.save(local("tester01", "first@example.com"));

        assertThatThrownBy(() -> repository.save(local("tester01", "second@example.com")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.USERNAME_DUPLICATE));
    }

    @Test
    void duplicateLocalEmailIsConvertedToEmailConflict() {
        repository.save(local("tester01", "same@example.com"));

        assertThatThrownBy(() -> repository.save(local("tester02", "same@example.com")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MemberErrorCode.EMAIL_DUPLICATE));
    }

    @Test
    void otherIntegrityViolationsAreNotConvertedToDuplicateErrors() {
        String tooLongEmail = "a".repeat(250) + "@example.com"; // VARCHAR(255) 초과

        // 중복 오류로 바꾸지 않고, DB 오류 상세(입력값·해시가 든 "Failing row contains ...")를 예외에 싣지 않는다
        assertThatThrownBy(() -> repository.save(local("tester09", tooLongEmail)))
                .isNotInstanceOf(BusinessException.class)
                .isInstanceOf(IllegalStateException.class)
                .hasNoCause()
                .hasMessageNotContaining(tooLongEmail);
    }

    @Test
    void googleAccountMayShareEmailWithLocalAccount() {
        repository.save(local("tester01", "shared@example.com"));

        jdbc.update("""
                INSERT INTO members (email, email_verified, nickname, role, account_type, account_status,
                                     created_at, updated_at)
                VALUES ('shared@example.com', true, '구글회원', 'USER', 'GOOGLE', 'ACTIVE', now(), now())
                """);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE email = 'shared@example.com'", Long.class))
                .isEqualTo(2L);
        assertThat(repository.existsLocalEmail("shared@example.com")).isTrue();
    }

    @Test
    void localAccountWithoutPasswordHashViolatesCheckConstraintNotDuplicate() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO members (username, email, email_verified, nickname, role, account_type, account_status,
                                     created_at, updated_at)
                VALUES ('nopass01', 'nopass@example.com', false, '무비번', 'USER', 'LOCAL', 'PENDING', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_members_local_credentials");
    }

    @Test
    void unknownAccountStatusViolatesCheckConstraint() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO members (email, email_verified, nickname, role, account_type, account_status,
                                     created_at, updated_at)
                VALUES ('x@example.com', false, 'x', 'USER', 'GOOGLE', 'DELETED', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_members_account_status");
    }

    @Test
    void oauthAccountProviderUserIdIsUnique() {
        Long memberId = jdbc.queryForObject("""
                INSERT INTO members (email, email_verified, nickname, role, account_type, account_status,
                                     created_at, updated_at)
                VALUES ('g@example.com', true, 'g', 'USER', 'GOOGLE', 'ACTIVE', now(), now()) RETURNING id
                """, Long.class);
        String insertOauth = """
                INSERT INTO member_oauth_accounts (member_id, provider, provider_user_id, email, created_at)
                VALUES (?, 'GOOGLE', 'sub-123', NULL, now())
                """;
        jdbc.update(insertOauth, memberId);

        assertThatThrownBy(() -> jdbc.update(insertOauth, memberId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_member_oauth_accounts_provider_user");
    }

    private static Member local(String username, String email) {
        return Member.registerLocal(username, "pbkdf2-sha256$test-hash", email, "닉네임");
    }
}
