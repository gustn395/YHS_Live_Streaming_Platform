package com.yhs.livestreaming.global.auth;

/**
 * 인증 주체: 검증을 통과한 요청의 회원 식별 정보(G-D006). SecurityContext의 principal로 들어가고,
 * 컨트롤러는 {@link CurrentMember}로 받는다.
 *
 * @param memberId  내부 회원 ID(양수)
 * @param role      회원 역할(예: USER)
 * @param sessionId 로그인 세션 ID. 세션 검증({@link LoginSessionVerifier})에 쓴다
 */
public record AuthenticatedMember(long memberId, String role, String sessionId) {

    public AuthenticatedMember {
        if (memberId <= 0) {
            throw new IllegalArgumentException("memberId는 양수여야 한다");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role이 비어 있다");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId가 비어 있다");
        }
    }
}
