package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.member.domain.OAuthAccount;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OAuthAccount a set a.email = :email where a.memberId = :memberId and a.provider = :provider")
    int updateEmail(@Param("memberId") long memberId, @Param("provider") String provider, @Param("email") String email);
}
