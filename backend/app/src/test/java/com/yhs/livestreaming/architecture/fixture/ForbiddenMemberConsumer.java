package com.yhs.livestreaming.architecture.fixture;

import com.yhs.livestreaming.member.domain.Member;

/** 테스트 전용: member 내부(Entity)를 직접 참조하는 다른 모듈 코드의 예. ModuleBoundaryTest가 위반으로 잡아야 한다. */
public class ForbiddenMemberConsumer {

    public String nicknameOf(Member member) {
        return member.getNickname();
    }
}
