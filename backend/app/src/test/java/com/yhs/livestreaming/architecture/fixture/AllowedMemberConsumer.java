package com.yhs.livestreaming.architecture.fixture;

import com.yhs.livestreaming.member.app.api.MemberQueryApi;
import com.yhs.livestreaming.member.app.api.MemberSummary;
import java.util.Optional;

/** 테스트 전용: member 공개 인터페이스만 쓰는 다른 모듈 코드의 예. ModuleBoundaryTest가 통과시켜야 한다. */
public class AllowedMemberConsumer {

    private final MemberQueryApi members;

    public AllowedMemberConsumer(MemberQueryApi members) {
        this.members = members;
    }

    public Optional<String> nicknameOf(long memberId) {
        return members.findById(memberId).map(MemberSummary::nickname);
    }
}
