package com.yhs.livestreaming.global.archfixture;

import com.yhs.livestreaming.member.app.api.MemberQueryApi;

/**
 * 테스트 전용: global 패키지에서 도메인 모듈(member 공개 인터페이스라도)을 참조하는 예. ModuleBoundaryTest가 G-D001 위반으로
 * 잡아야 한다.
 */
public class ForbiddenGlobalDependency {

    public boolean isActive(MemberQueryApi members, long memberId) {
        return members.isActive(memberId);
    }
}
