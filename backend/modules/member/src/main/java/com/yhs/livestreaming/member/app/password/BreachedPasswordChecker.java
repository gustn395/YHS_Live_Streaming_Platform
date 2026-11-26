package com.yhs.livestreaming.member.app.password;

/**
 * 유출 비밀번호 검사 포트(MB-D010). 구현은 비밀번호 원문·전체 해시를 외부로 보내거나 로그에 남기지 않고,
 * 확인하지 못한 경우를 통과로 돌려주지 않는다.
 */
public interface BreachedPasswordChecker {

    Result check(String rawPassword);

    enum Result {
        /** 알려진 유출 자료에 있다. 가입을 거부한다. */
        BREACHED,
        /** 유출 자료에서 찾지 못했다. */
        NOT_BREACHED,
        /** 시간 초과·비정상 응답 등으로 확인하지 못했다. 통과로 보지 않는다(fail-closed). */
        UNAVAILABLE
    }
}
