package com.yhs.livestreaming.member.app.api.archfixture;

import com.yhs.livestreaming.member.domain.AccountStatus;

/**
 * 테스트 전용: 공개 패키지(member.app.api) 안에서 member 내부 타입을 노출하는 계약의 예. ModuleBoundaryTest가 위반으로 잡아야
 * 한다(공개 계약이 내부 타입을 드러내면 다른 모듈이 그 타입에 묶인다).
 */
public interface LeakyPublicContract {

    AccountStatus statusOf(long memberId);
}
