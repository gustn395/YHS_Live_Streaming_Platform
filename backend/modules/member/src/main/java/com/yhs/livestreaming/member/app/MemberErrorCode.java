package com.yhs.livestreaming.member.app;

import com.yhs.livestreaming.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** member 모듈 오류 코드(G-D003, MB-D008, MB-D010). detail은 사용자에게 보여 줄 기본 문구다. */
public enum MemberErrorCode implements ErrorCode {
    USERNAME_DUPLICATE(HttpStatus.CONFLICT, "MEMBER_USERNAME_DUPLICATE", "이미 사용 중인 아이디입니다."),
    EMAIL_DUPLICATE(HttpStatus.CONFLICT, "MEMBER_EMAIL_DUPLICATE", "이미 가입된 이메일입니다."),
    PASSWORD_BREACHED(HttpStatus.BAD_REQUEST, "MEMBER_PASSWORD_BREACHED",
            "유출된 적이 있는 비밀번호라 쓸 수 없습니다. 다른 비밀번호를 입력해 주세요."),
    PASSWORD_CHECK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MEMBER_PASSWORD_CHECK_UNAVAILABLE",
            "지금은 비밀번호 안전 여부를 확인할 수 없어 가입할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String detail;

    MemberErrorCode(HttpStatus status, String code, String detail) {
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }
}
