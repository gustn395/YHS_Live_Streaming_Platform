package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.member.domain.OAuthAccount;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OAuthAccountJpaRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OAuthAccount a where a.provider = :provider and a.providerUserId = :sub "
            + "and a.memberId = :memberId")
    int deleteLink(@Param("provider") String provider, @Param("sub") String sub, @Param("memberId") long memberId);

    /** 정리 배치(MB-D030): 지정한 회원들의 연결 행을 지운다(호출자 트랜잭션, 회원 행보다 먼저, MB-D025). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OAuthAccount a where a.provider = :provider and a.memberId in :memberIds")
    int deleteLinksOf(@Param("provider") String provider, @Param("memberIds") Collection<Long> memberIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OAuthAccount a set a.email = :email where a.memberId = :memberId and a.provider = :provider")
    int updateEmail(@Param("memberId") long memberId, @Param("provider") String provider, @Param("email") String email);
}
