package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.member.app.oauth.GoogleAccountRepository;
import com.yhs.livestreaming.member.app.oauth.GoogleLinkConflictException;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import com.yhs.livestreaming.member.domain.OAuthAccount;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GOOGLE 회원·OAuth 연결 JPA 어댑터(MB-D025). 메서드마다 트랜잭션 하나를 직접 연다: 제약 위반은 커밋 전에 flush로 드러나고,
 * 트랜잭션이 롤백된 뒤에 provider·sub UNIQUE 충돌만 {@link GoogleLinkConflictException}으로 바꾼다. 그 밖의 제약 위반은 제약
 * 이름·SQLState만 남기고 원인 없는 {@link IllegalStateException}이다(DB 상세에 든 이메일을 로그·응답에 싣지 않으려고).
 */
@Repository
public class GoogleAccountRepositoryAdapter implements GoogleAccountRepository {

    static final String LINK_UNIQUE = "uk_member_oauth_accounts_provider_user";
    private static final Logger log = LoggerFactory.getLogger(GoogleAccountRepositoryAdapter.class);

    private final MemberJpaRepository members;
    private final OAuthAccountJpaRepository links;
    private final TransactionTemplate tx;

    GoogleAccountRepositoryAdapter(MemberJpaRepository members, OAuthAccountJpaRepository links,
                                   PlatformTransactionManager transactionManager) {
        this.members = members;
        this.links = links;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<Member> findBySub(String sub) {
        return tx.execute(status -> links.findByProviderAndProviderUserId(OAuthAccount.GOOGLE, sub)
                .flatMap(link -> members.findById(link.getMemberId())));
    }

    @Override
    public Member create(Member newMember, String sub, String linkEmail) {
        return translated(() -> tx.execute(status -> insert(newMember, sub, linkEmail)));
    }

    @Override
    public Optional<Member> replaceExpiredPending(long expiredMemberId, String sub, Instant expiredAtOrBefore,
                                                  Member newMember, String linkEmail) {
        return translated(() -> tx.execute(status -> {
            // 연결 → 회원 순서로 지운다(FK). 둘 다 정확히 1행이어야 한다: 아니면 그 사이 상태가 바뀐 것이라 전체 롤백
            int link = links.deleteLink(OAuthAccount.GOOGLE, sub, expiredMemberId);
            int member = link == 1 ? members.deleteExpiredPending(expiredMemberId, expiredAtOrBefore,
                    AccountType.GOOGLE, AccountStatus.PENDING) : 0;
            if (link != 1 || member != 1) {
                status.setRollbackOnly();
                return Optional.<Member>empty();
            }
            return Optional.of(insert(newMember, sub, linkEmail));
        }));
    }

    @Override
    public int activatePending(long memberId, String email, Instant createdAfter, Instant now) {
        return tx.execute(status -> {
            int rows = members.activatePendingWithEmail(memberId, email, createdAfter, now, AccountType.GOOGLE,
                    AccountStatus.PENDING, AccountStatus.ACTIVE);
            return withLinkEmail(status, rows, memberId, email);
        });
    }

    @Override
    public int updateActiveEmail(long memberId, String email, Instant now) {
        return tx.execute(status -> {
            int rows = members.updateEmailOfActive(memberId, email, now, AccountType.GOOGLE, AccountStatus.ACTIVE);
            return withLinkEmail(status, rows, memberId, email);
        });
    }

    /** 회원 행이 바뀌었으면 연결 행도 정확히 1행 바뀌어야 한다. 아니면 회원 변경까지 롤백하고 0을 돌려준다. */
    private int withLinkEmail(TransactionStatus status, int memberRows, long memberId, String email) {
        if (memberRows != 1) {
            return memberRows;
        }
        if (links.updateEmail(memberId, OAuthAccount.GOOGLE, email) != 1) {
            status.setRollbackOnly();
            return 0;
        }
        return 1;
    }

    private Member insert(Member newMember, String sub, String linkEmail) {
        Member saved = members.saveAndFlush(newMember);
        links.saveAndFlush(OAuthAccount.google(saved.getId(), sub, linkEmail));
        return saved;
    }

    private static <T> T translated(java.util.function.Supplier<T> work) {
        try {
            return work.get();
        } catch (DataIntegrityViolationException ex) {
            Optional<ConstraintViolationException> violation = constraintViolation(ex);
            String constraint = violation.map(ConstraintViolationException::getConstraintName).orElse("");
            if (LINK_UNIQUE.equalsIgnoreCase(constraint)) {
                throw new GoogleLinkConflictException();
            }
            log.warn("GOOGLE 회원 저장 중 DB 제약 위반: constraint={}, sqlState={}",
                    constraint.isEmpty() ? "unknown" : constraint,
                    violation.map(ConstraintViolationException::getSQLState).orElse("unknown"));
            throw new IllegalStateException("GOOGLE 회원 저장 중 DB 제약 위반(" + (constraint.isEmpty() ? "unknown" : constraint)
                    + ")");
        }
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
