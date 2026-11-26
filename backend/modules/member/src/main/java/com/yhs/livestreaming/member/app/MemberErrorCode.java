package com.yhs.livestreaming.member.app;

import com.yhs.livestreaming.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * member 모듈 오류 코드(G-D003, MB-D008, MB-D010, MB-D011, MB-D015~D017). detail은 사용자에게 보여 줄 기본 문구다.
 * 로그인·OTP 코드는 인증 흐름 코드라 {@code AUTH_} 접두사를 쓴다(이름은 BE-03 ④ 승인).
 */
public enum MemberErrorCode implements ErrorCode {
    USERNAME_DUPLICATE(HttpStatus.CONFLICT, "MEMBER_USERNAME_DUPLICATE", "이미 사용 중인 아이디입니다."),
    EMAIL_DUPLICATE(HttpStatus.CONFLICT, "MEMBER_EMAIL_DUPLICATE", "이미 가입된 이메일입니다."),
    PASSWORD_BREACHED(HttpStatus.BAD_REQUEST, "MEMBER_PASSWORD_BREACHED",
            "유출된 적이 있는 비밀번호라 쓸 수 없습니다. 다른 비밀번호를 입력해 주세요."),
    PASSWORD_CHECK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MEMBER_PASSWORD_CHECK_UNAVAILABLE",
            "지금은 비밀번호 안전 여부를 확인할 수 없어 가입할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    // 로그인(MB-D011): 비밀번호 불일치·없는 username·GOOGLE 계정·PENDING 만료는 모두 같은 응답이다
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH_LOGIN_FAILED", "아이디 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_TEMPORARILY_BLOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOGIN_TEMPORARILY_BLOCKED",
            "로그인 실패가 많아 잠시 로그인할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "MEMBER_ACCOUNT_DISABLED", "사용이 중지된 계정입니다."),
    // OTP(MB-D015~D017): 시도 소진·회원 단위 제한·Challenge 만료를 서로 다른 코드로 구분한다
    OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_OTP_RATE_LIMITED",
            "인증 번호 요청·확인 횟수를 넘었습니다. 잠시 후 다시 시도해 주세요."),
    OTP_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_OTP_MISMATCH", "인증 번호가 올바르지 않습니다."),
    OTP_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_OTP_EXPIRED", "인증 번호가 만료되었습니다. 인증 번호를 다시 받아 주세요."),
    OTP_CHALLENGE_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_OTP_CHALLENGE_INVALID",
            "인증 시간이 지났습니다. 다시 로그인해 주세요."),
    OTP_DELIVERY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "MEMBER_OTP_DELIVERY_UNAVAILABLE",
            "지금은 인증 메일을 보낼 수 없습니다. 잠시 후 다시 시도해 주세요.");

    /** OTP 시도 횟수를 다 쓴 경우의 안내(코드는 {@link #OTP_EXPIRED}와 같고 문구만 다르다). */
    public static final String OTP_EXHAUSTED_DETAIL = "인증 번호 입력 횟수를 모두 사용했습니다. 인증 번호를 다시 받아 주세요.";
    /** 검증 중 새 인증 번호가 발송된 경우의 안내. */
    public static final String OTP_REPLACED_DETAIL = "새 인증 번호가 발송되었습니다. 최근에 받은 인증 번호를 입력해 주세요.";

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
