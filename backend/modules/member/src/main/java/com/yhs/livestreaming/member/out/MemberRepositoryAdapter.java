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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate tx;

    MemberRepositoryAdapter(MemberJpaRepository jpa, PlatformTransactionManager transactionManager) {
        this.jpa = jpa;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public boolean isUsernameTaken(String username, Instant expiredAtOrBefore) {
        return jpa.existsUsernameHolder(username, expiredAtOrBefore, AccountType.LOCAL, AccountStatus.PENDING);
    }

    @Override
    public boolean isLocalEmailTaken(String email, Instant expiredAtOrBefore) {
        return jpa.existsLocalEmailHolder(email, expiredAtOrBefore, AccountType.LOCAL, AccountStatus.PENDING);
    }

    /**
     * 만료 LOCAL PENDING 회수와 저장을 한 트랜잭션에서 한다(MB-D030). 삭제는 LOCAL·PENDING·만료 조건을 문장 안에서 다시 확인한다.
     * 저장이 실패하면 트랜잭션 전체가 롤백되어 회수한 회원도 남고, 예외 변환은 롤백이 끝난 뒤 {@link #save}와 같은 규칙으로 한다.
     */
    @Override
    public Member saveReclaimingExpired(Member member, Instant expiredAtOrBefore) {
        try {
            return tx.execute(status -> {
                jpa.deleteExpiredLocalHolders(member.getUsername(), member.getEmail(), expiredAtOrBefore,
                        AccountType.LOCAL, AccountStatus.PENDING);
                return jpa.saveAndFlush(member);
            });
        } catch (DataIntegrityViolationException ex) {
            throw translated(ex);
        }
    }

    @Override
    public Member save(Member member) {
        try {
            return jpa.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            throw translated(ex);
        }
    }

    /** username·LOCAL 이메일 UNIQUE 위반만 409로 바꾸고, 그 밖의 위반은 상세 없는 서버 오류로 바꾼다. */
    private static RuntimeException translated(DataIntegrityViolationException ex) {
        Optional<ConstraintViolationException> violation = constraintViolation(ex);
        String constraint = violation.map(ConstraintViolationException::getConstraintName).orElse("");
        if (USERNAME_UNIQUE.equalsIgnoreCase(constraint)) {
            return duplicate(MemberErrorCode.USERNAME_DUPLICATE);
        }
        if (LOCAL_EMAIL_UNIQUE.equalsIgnoreCase(constraint)) {
            return duplicate(MemberErrorCode.EMAIL_DUPLICATE);
        }
        String sqlState = violation.map(ConstraintViolationException::getSQLState).orElse("unknown");
        log.warn("회원 저장 중 DB 제약 위반: constraint={}, sqlState={}", constraint.isEmpty() ? "unknown" : constraint,
                sqlState);
        return new IllegalStateException("회원 저장 중 DB 제약 위반(" + (constraint.isEmpty() ? "unknown" : constraint) + ")");
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
