package com.yhs.livestreaming.member.app.oauth;

/**
 * 같은 provider·sub 연결이 이미 있어 새 GOOGLE 회원을 만들지 못했다(동시 최초 로그인, MB-D025). 실패한 트랜잭션은 롤백됐으므로
 * 호출자는 새 트랜잭션에서 기존 회원을 다시 조회한다. 다른 DB 제약 위반은 이 예외가 아니다.
 */
public class GoogleLinkConflictException extends RuntimeException {

    public GoogleLinkConflictException() {
        super("같은 Google 계정 연결이 이미 있다");
    }
}
