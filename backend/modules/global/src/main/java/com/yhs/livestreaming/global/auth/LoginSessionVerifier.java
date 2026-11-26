package com.yhs.livestreaming.global.auth;

/**
 * 로그인 세션 검증 포트(G-D006, G-D007). 인터페이스는 global에 두고 구현은 member가 한다(MB-W05~W08).
 *
 * <p>계약:
 * <ul>
 *     <li>허용 목록 방식이다. 세션이 저장소에 있을 때만 {@link LoginSessionStatus#ACTIVE}다.</li>
 *     <li>세션이 없으면 {@link LoginSessionStatus#NOT_FOUND}(401), 세션 소유 회원이 JWT의 memberId와 다르면
 *         {@link LoginSessionStatus#MEMBER_MISMATCH}(401)다.</li>
 *     <li>저장소 오류는 상태로 돌려주지 않고 {@link LoginSessionStoreUnavailableException}을 던진다(503). 오류를
 *         통과로 처리하지 않는다.</li>
 * </ul>
 */
public interface LoginSessionVerifier {

    LoginSessionStatus verify(String sessionId, long memberId);
}
