package com.yhs.livestreaming.member.app;

/**
 * 회원가입 입력. 형식 검증·정규화·비밀번호 정책 검사는 입력 계층에서 끝난 값이다(MB-D004, MB-D008).
 * {@link #toString()}은 비밀번호를 가린다(원문 로깅 금지).
 */
public record SignupCommand(String username, String rawPassword, String email, String nickname) {

    @Override
    public String toString() {
        return "SignupCommand[username=" + username + ", rawPassword=****, email=" + email
                + ", nickname=" + nickname + "]";
    }
}
