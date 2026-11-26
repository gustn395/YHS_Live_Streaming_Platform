package com.yhs.livestreaming.member.app.limit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 요청·로그인·OTP 제한 수치(MB-D015, MB-D017, MB-D018). 기본값이 정책 값이고 설정({@code member.limits.*})으로 바꿀 수 있다.
 * 비밀 값이 아니므로 기본값을 둔다.
 */
@ConfigurationProperties("member.limits")
public record MemberLimitProperties(Window signup, Window login, Window otp, LoginFailure loginFailure,
                                    OtpIssue otpIssue) {

    public MemberLimitProperties {
        signup = signup != null ? signup : new Window(5, Duration.ofHours(1));
        login = login != null ? login : new Window(30, Duration.ofMinutes(10));
        otp = otp != null ? otp : new Window(30, Duration.ofMinutes(10));
        loginFailure = loginFailure != null ? loginFailure
                : new LoginFailure(10, Duration.ofHours(24), Duration.ofMinutes(15));
        otpIssue = otpIssue != null ? otpIssue : new OtpIssue(Duration.ofSeconds(60), 5, 20, 10, 5);
    }

    public static MemberLimitProperties defaults() {
        return new MemberLimitProperties(null, null, null, null, null);
    }

    public Window window(RateLimitScope scope) {
        return switch (scope) {
            case SIGNUP -> signup;
            case LOGIN -> login;
            case OTP -> otp;
        };
    }

    /** IP 기준 고정 창: 창 안에서 {@code max}번까지 허용. */
    public record Window(int max, Duration window) {
    }

    /** 로그인 실패: 연속 {@code maxFailures}번째 실패에 {@code block} 동안 차단, 카운터는 마지막 실패 + {@code counterTtl}. */
    public record LoginFailure(int maxFailures, Duration counterTtl, Duration block) {
    }

    /**
     * 회원 단위 OTP: 발급 간격, 시간당·24시간 발급 수(새 로그인 + 재발송), 24시간 검증 실패 합산, OTP당 시도 수.
     */
    public record OtpIssue(Duration interval, int hourlyMax, int dailyMax, int dailyFailureMax, int attemptsPerOtp) {
    }
}
