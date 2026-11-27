package com.yhs.livestreaming.member.app.oauth;

import com.yhs.livestreaming.member.domain.Member;
import java.time.Instant;
import java.util.Optional;

/**
 * GOOGLE 회원·OAuth 연결 저장 포트(MB-D025). 메서드마다 자기 DB 트랜잭션 하나로 끝난다.
 */
public interface GoogleAccountRepository {

    /** provider=GOOGLE·sub로 연결된 회원. */
    Optional<Member> findBySub(String sub);

    /**
     * 회원과 연결 행을 같은 트랜잭션에서 만든다.
     *
     * @throws GoogleLinkConflictException 같은 sub 연결이 이미 있다(동시 최초 로그인). 트랜잭션은 롤백됐다
     */
    Member create(Member newMember, String sub, String linkEmail);

    /**
     * 만료 PENDING GOOGLE 회원을 같은 트랜잭션에서 지우고 새 회원·연결 행을 만든다. 삭제 직전에 GOOGLE·PENDING·
     * {@code createdAt <= expiredAtOrBefore}·sub 연결을 다시 확인하고, 하나라도 맞지 않으면 전체를 롤백하고 비어 있다.
     */
    Optional<Member> replaceExpiredPending(long expiredMemberId, String sub, Instant expiredAtOrBefore, Member newMember,
                                           String linkEmail);

    /**
     * OTP 최종 성공: 만료 전 PENDING GOOGLE 회원을 ACTIVE·이메일 확인으로 바꾸고 회원·연결 행 이메일을 갱신한다(같은 트랜잭션).
     *
     * @return 바뀐 회원 행 수(0이면 상태를 다시 확인해야 한다)
     */
    int activatePending(long memberId, String email, Instant createdAfter, Instant now);

    /**
     * OTP 최종 성공: ACTIVE GOOGLE 회원의 이메일을 회원·연결 행 모두 갱신한다(같은 트랜잭션).
     *
     * @return 바뀐 회원 행 수(ACTIVE GOOGLE이 아니면 0)
     */
    int updateActiveEmail(long memberId, String email, Instant now);
}
