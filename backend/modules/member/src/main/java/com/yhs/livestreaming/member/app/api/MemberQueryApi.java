package com.yhs.livestreaming.member.app.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 다른 모듈용 회원 조회 공개 인터페이스(MB-D029). 다른 모듈은 member를 이 패키지로만 참조한다(G-D015).
 * 구현은 member 내부 Bean이다.
 */
public interface MemberQueryApi {

    /** 일괄 조회 한 번에 넘길 수 있는 최대 ID 수. */
    int MAX_BATCH_SIZE = 100;

    /** 회원 요약. 없으면 비어 있다. */
    Optional<MemberSummary> findById(long memberId);

    /**
     * 여러 회원의 요약. 없는 ID는 결과에서 빠지고, 같은 ID는 한 번만 담는다. 순서는 보장하지 않는다.
     *
     * @throws IllegalArgumentException ID가 {@link #MAX_BATCH_SIZE}개(중복 포함)를 넘을 때
     */
    List<MemberSummary> findAllByIds(Collection<Long> memberIds);

    /** ACTIVE 회원일 때만 참이다(PENDING·DISABLED·없음은 거짓). */
    boolean isActive(long memberId);
}
