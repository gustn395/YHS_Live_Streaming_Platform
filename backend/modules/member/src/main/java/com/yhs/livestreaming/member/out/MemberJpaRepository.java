package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface MemberJpaRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmailAndAccountType(String email, AccountType accountType);

    Optional<Member> findByUsername(String username);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Member m set m.passwordHash = :newHash, m.updatedAt = :now "
            + "where m.id = :id and m.passwordHash = :expectedHash")
    int updatePasswordHash(@Param("id") long id, @Param("expectedHash") String expectedHash,
                           @Param("newHash") String newHash, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Member m set m.accountStatus = :active, m.emailVerified = true, m.updatedAt = :now "
            + "where m.id = :id and m.accountStatus = :pending and m.createdAt > :createdAfter")
    int activatePending(@Param("id") long id, @Param("createdAfter") Instant createdAfter, @Param("now") Instant now,
                        @Param("pending") AccountStatus pending, @Param("active") AccountStatus active);

    /** GOOGLE 전용(MB-D025): 호출자 트랜잭션 안에서 쓴다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Member m set m.accountStatus = :active, m.emailVerified = true, m.email = :email, m.updatedAt = :now "
            + "where m.id = :id and m.accountType = :type and m.accountStatus = :pending and m.createdAt > :createdAfter")
    int activatePendingWithEmail(@Param("id") long id, @Param("email") String email,
                                 @Param("createdAfter") Instant createdAfter, @Param("now") Instant now,
                                 @Param("type") AccountType type, @Param("pending") AccountStatus pending,
                                 @Param("active") AccountStatus active);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Member m set m.email = :email, m.updatedAt = :now "
            + "where m.id = :id and m.accountType = :type and m.accountStatus = :active")
    int updateEmailOfActive(@Param("id") long id, @Param("email") String email, @Param("now") Instant now,
                            @Param("type") AccountType type, @Param("active") AccountStatus active);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Member m where m.id = :id and m.accountType = :type and m.accountStatus = :pending "
            + "and m.createdAt <= :expiredAtOrBefore")
    int deleteExpiredPending(@Param("id") long id, @Param("expiredAtOrBefore") Instant expiredAtOrBefore,
                             @Param("type") AccountType type, @Param("pending") AccountStatus pending);
}
