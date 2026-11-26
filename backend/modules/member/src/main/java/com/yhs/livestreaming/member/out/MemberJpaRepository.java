package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

interface MemberJpaRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmailAndAccountType(String email, AccountType accountType);
}
