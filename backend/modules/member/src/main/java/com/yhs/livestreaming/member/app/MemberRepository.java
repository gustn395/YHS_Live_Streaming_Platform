package com.yhs.livestreaming.member.app;

import com.yhs.livestreaming.member.domain.Member;

/**
 * 회원 저장 포트(app → out). 구현은 out 패키지의 JPA 어댑터다(backend/README.md 5장).
 * {@link #save}는 username·LOCAL 이메일 UNIQUE 위반을 {@link MemberErrorCode}의 409 오류로 바꿔 던진다(MB-D008).
 */
public interface MemberRepository {

    boolean existsByUsername(String username);

    boolean existsLocalEmail(String email);

    Member save(Member member);
}
