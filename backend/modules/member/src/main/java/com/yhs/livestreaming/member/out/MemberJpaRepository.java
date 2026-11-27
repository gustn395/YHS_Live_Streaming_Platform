package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface MemberJpaRepository extends JpaRepository<Member, Long> {

    /** 가입 사전 검사(MB-D030): 만료 LOCAL PENDING을 빼고 이 username을 쥔 회원이 있는지. */
    @Query("select count(m) > 0 from Member m where m.username = :username and not (m.accountType = :local "
            + "and m.accountStatus = :pending and m.createdAt <= :expiredAtOrBefore)")
    boolean existsUsernameHolder(@Param("username") String username,
                                 @Param("expiredAtOrBefore") Instant expiredAtOrBefore,
                                 @Param("local") AccountType local, @Param("pending") AccountStatus pending);

    /** 가입 사전 검사(MB-D030): 만료 PENDING을 빼고 이 이메일을 쥔 LOCAL 회원이 있는지. */
    @Query("select count(m) > 0 from Member m where m.email = :email and m.accountType = :local and not "
            + "(m.accountStatus = :pending and m.createdAt <= :expiredAtOrBefore)")
    boolean existsLocalEmailHolder(@Param("email") String email, @Param("expiredAtOrBefore") Instant expiredAtOrBefore,
                                   @Param("local") AccountType local, @Param("pending") AccountStatus pending);

    /** LOCAL 가입 시 즉시 회수(MB-D030): 호출자 트랜잭션 안에서 이 username 또는 이메일을 쥔 만료 LOCAL PENDING을 지운다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Member m where m.accountType = :local and m.accountStatus = :pending "
            + "and m.createdAt <= :expiredAtOrBefore and (m.username = :username or m.email = :email)")
    int deleteExpiredLocalHolders(@Param("username") String username, @Param("email") String email,
                                  @Param("expiredAtOrBefore") Instant expiredAtOrBefore,
                                  @Param("local") AccountType local, @Param("pending") AccountStatus pending);

    /** 정리 배치 대상(MB-D030): ID 순서 커서, OFFSET 없음. */
    @Query("select m.id from Member m where m.accountType = :type and m.accountStatus = :pending "
            + "and m.createdAt <= :expiredAtOrBefore and m.id > :afterId order by m.id")
    List<Long> findExpiredPendingIds(@Param("type") AccountType type, @Param("pending") AccountStatus pending,
                                     @Param("expiredAtOrBefore") Instant expiredAtOrBefore,
                                     @Param("afterId") long afterId, Limit limit);

    /** 정리 배치(MB-D030): 고른 ID 중 지금도 조건을 만족하는 회원 ID(호출자 트랜잭션). */
    @Query("select m.id from Member m where m.id in :ids and m.accountType = :type and m.accountStatus = :pending "
            + "and m.createdAt <= :expiredAtOrBefore")
    List<Long> findStillExpiredPendingIds(@Param("ids") Collection<Long> ids, @Param("type") AccountType type,
                                          @Param("pending") AccountStatus pending,
                                          @Param("expiredAtOrBefore") Instant expiredAtOrBefore);

    /** 정리 배치(MB-D030): 고른 ID 중 지금도 조건을 만족하는 회원만 지운다(호출자 트랜잭션). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Member m where m.id in :ids and m.accountType = :type and m.accountStatus = :pending "
            + "and m.createdAt <= :expiredAtOrBefore")
    int deleteExpiredPendingIn(@Param("ids") Collection<Long> ids, @Param("type") AccountType type,
                               @Param("pending") AccountStatus pending,
                               @Param("expiredAtOrBefore") Instant expiredAtOrBefore);

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

    /** 닉네임 수정(MB-D028): ACTIVE 회원만 바꾼다. 상태 확인과 변경을 한 문장으로 해 그 사이 정지된 회원을 바꾸지 않는다. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Member m set m.nickname = :nickname, m.updatedAt = :now "
            + "where m.id = :id and m.accountStatus = :active")
    int updateNicknameOfActive(@Param("id") long id, @Param("nickname") String nickname, @Param("now") Instant now,
                               @Param("active") AccountStatus active);
}
