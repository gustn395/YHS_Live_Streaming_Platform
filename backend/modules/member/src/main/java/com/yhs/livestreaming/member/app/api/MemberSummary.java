package com.yhs.livestreaming.member.app.api;

/**
 * 다른 모듈에 주는 회원 요약(MB-D029). 표시·권한 판단에 필요한 값만 담고 email·username은 담지 않는다.
 *
 * @param memberId      내부 회원 ID
 * @param nickname      닉네임(MB-D008)
 * @param role          회원 역할(예: USER). 인증 주체 {@code AuthenticatedMember.role}과 같은 문자열이다(G-D006)
 * @param accountStatus 계정 상태
 */
public record MemberSummary(long memberId, String nickname, String role, MemberAccountStatus accountStatus) {
}
