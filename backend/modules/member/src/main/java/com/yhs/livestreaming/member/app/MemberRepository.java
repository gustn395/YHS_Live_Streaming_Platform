package com.yhs.livestreaming.member.app;

import com.yhs.livestreaming.member.domain.Member;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 회원 저장 포트(app → out). 구현은 out 패키지의 JPA 어댑터다(backend/README.md 5장).
 * {@link #save}는 username·LOCAL 이메일 UNIQUE 위반을 {@link MemberErrorCode}의 409 오류로 바꿔 던진다(MB-D008).
 */
public interface MemberRepository {

    /**
     * 가입 사전 중복 검사(MB-D008, MB-D030): 이 username을 쥔 회원이 있으면 참이다. 단 LOCAL·PENDING이고 가입 시각이
     * {@code expiredAtOrBefore} 이하(만료)인 회원은 회수 대상이라 세지 않는다.
     */
    boolean isUsernameTaken(String username, Instant expiredAtOrBefore);

    /** 가입 사전 중복 검사: LOCAL 회원 이메일 기준, 만료 LOCAL PENDING은 세지 않는다(MB-D030). */
    boolean isLocalEmailTaken(String email, Instant expiredAtOrBefore);

    /**
     * LOCAL 가입 시 즉시 회수(MB-D030): 한 트랜잭션에서 이 회원의 username·이메일을 쥔 만료 LOCAL PENDING 회원을 조건부로
     * 지우고 새 회원을 저장한다. 저장이 실패하면 삭제도 롤백되고, UNIQUE 위반은 {@link #save}와 같은 409다.
     */
    Member saveReclaimingExpired(Member member, Instant expiredAtOrBefore);

    Member save(Member member);

    Optional<Member> findByUsername(String username);

    Optional<Member> findById(long id);

    /**
     * 비밀번호 재해시(MB-D006): 저장된 해시가 {@code expectedHash}일 때만 바꾼다(짧은 트랜잭션 하나).
     *
     * @return 바뀐 행 수(0이면 그 사이 다른 변경이 있었다)
     */
    int updatePasswordHash(long id, String expectedHash, String newHash, Instant now);

    /**
     * 첫 OTP 성공 활성화(MB-D003): PENDING이고 {@code createdAfter}보다 뒤에 가입한(만료 전) 회원만 ACTIVE·이메일 확인으로 바꾼다.
     *
     * @return 바뀐 행 수(0이면 상태를 다시 확인해야 한다)
     */
    int activatePending(long id, Instant createdAfter, Instant now);

    /** 여러 회원을 한 번에 조회한다(MB-D029). 없는 ID는 결과에서 빠진다. */
    List<Member> findAllByIds(Collection<Long> ids);

    /**
     * 닉네임 수정(MB-D028): ACTIVE 회원일 때만 바꾼다(짧은 트랜잭션 하나).
     *
     * @return 바뀐 행 수(0이면 회원이 없거나 ACTIVE가 아니다)
     */
    int updateNicknameOfActive(long id, String nickname, Instant now);
}
