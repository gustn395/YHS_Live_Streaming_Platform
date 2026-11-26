package com.yhs.livestreaming.member.app.password;

/**
 * 비밀번호 정책 위반(MB-D004, MB-D005). 메시지는 사용자에게 보여 주는 거부 이유다(NIST 3.1.1.2: 거부 이유를 알려야 한다).
 * 메시지에는 입력값을 넣지 않는다.
 */
public enum PasswordViolation {
    TOO_SHORT("비밀번호는 15자 이상이어야 합니다."),
    TOO_LONG("비밀번호는 64자 이하여야 합니다."),
    CONTEXT_WORD("아이디나 서비스 이름을 바탕으로 한 비밀번호는 쓸 수 없습니다.");

    private final String message;

    PasswordViolation(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
