package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * {@link MemberRepository}의 JPA 구현. 저장은 {@code saveAndFlush}로 즉시 INSERT해 UNIQUE 위반을 이 메서드 안에서 받는다.
 * Spring Data 리포지토리 메서드의 트랜잭션이 실패하면 롤백된 뒤 예외가 올라온다.
 *
 * <p>username·LOCAL 이메일 UNIQUE 위반만 409로 바꾼다(MB-D008). 그 밖의 제약 위반(CHECK·길이 등)은 중복 오류로 바꾸지 않고
 * 서버 오류로 던지되, DB 오류 상세("Failing row contains ..."에 이메일·비밀번호 해시가 들어 있다)는 예외에 싣지 않는다.
 * 진단용으로 제약 이름과 SQLState만 로그에 남긴다.
 */
@Repository
public class MemberRepositoryAdapter implements MemberRepository {

    static final String USERNAME_UNIQUE = "uk_members_username";
    static final String LOCAL_EMAIL_UNIQUE = "ux_members_local_email";

    private static final Logger log = LoggerFactory.getLogger(MemberRepositoryAdapter.class);

    private final MemberJpaRepository jpa;

    MemberRepositoryAdapter(MemberJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    @Override
    public boolean existsLocalEmail(String email) {
        return jpa.existsByEmailAndAccountType(email, AccountType.LOCAL);
    }

    @Override
    public Member save(Member member) {
        try {
            return jpa.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            Optional<ConstraintViolationException> violation = constraintViolation(ex);
            String constraint = violation.map(ConstraintViolationException::getConstraintName).orElse("");
            if (USERNAME_UNIQUE.equalsIgnoreCase(constraint)) {
                throw duplicate(MemberErrorCode.USERNAME_DUPLICATE);
            }
            if (LOCAL_EMAIL_UNIQUE.equalsIgnoreCase(constraint)) {
                throw duplicate(MemberErrorCode.EMAIL_DUPLICATE);
            }
            String sqlState = violation.map(ConstraintViolationException::getSQLState).orElse("unknown");
            log.warn("회원 저장 중 DB 제약 위반: constraint={}, sqlState={}", constraint.isEmpty() ? "unknown" : constraint,
                    sqlState);
            throw new IllegalStateException("회원 저장 중 DB 제약 위반(" + (constraint.isEmpty() ? "unknown" : constraint) + ")");
        }
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        return jpa.findByUsername(username);
    }

    @Override
    public Optional<Member> findById(long id) {
        return jpa.findById(id);
    }

    @Override
    public int updatePasswordHash(long id, String expectedHash, String newHash, Instant now) {
        return jpa.updatePasswordHash(id, expectedHash, newHash, now);
    }

    @Override
    public int activatePending(long id, Instant createdAfter, Instant now) {
        return jpa.activatePending(id, createdAfter, now, AccountStatus.PENDING, AccountStatus.ACTIVE);
    }

    @Override
    public List<Member> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findAllById(Set.copyOf(ids));
    }

    @Override
    public int updateNicknameOfActive(long id, String nickname, Instant now) {
        return jpa.updateNicknameOfActive(id, nickname, now, AccountStatus.ACTIVE);
    }

    private static BusinessException duplicate(MemberErrorCode errorCode) {
        return new BusinessException(errorCode, errorCode.detail());
    }

    private static Optional<ConstraintViolationException> constraintViolation(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return Optional.of(violation);
            }
        }
        return Optional.empty();
    }
}
