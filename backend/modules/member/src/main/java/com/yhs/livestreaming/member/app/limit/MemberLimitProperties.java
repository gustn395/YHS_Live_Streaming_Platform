package com.yhs.livestreaming.member.app.limit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 요청·로그인·OTP·Refresh 제한 수치(MB-D015, MB-D017, MB-D018, MB-D020). 기본값이 정책 값이고 설정({@code member.limits.*})으로 바꿀 수 있다.
 * 비밀 값이 아니므로 기본값을 둔다.
 */
@ConfigurationProperties("member.limits")
public record MemberLimitProperties(Window signup, Window login, Window otp, Window refresh, Window oauthStart,
                                    LoginFailure loginFailure, OtpIssue otpIssue) {

    public MemberLimitProperties {
        // 범위별 설정 객체가 없으면 기본값, 있으면 비운 max·기간만 그 범위의 기본값으로 채운다(설정한 값은 유지)
        signup = withDefaults(signup, new Window(5, Duration.ofHours(1)));
        login = withDefaults(login, new Window(30, Duration.ofMinutes(10)));
        otp = withDefaults(otp, new Window(30, Duration.ofMinutes(10)));
        refresh = withDefaults(refresh, new Window(120, Duration.ofMinutes(10))); // MB-D020
        oauthStart = withDefaults(oauthStart, new Window(30, Duration.ofMinutes(10))); // MB-D024
        loginFailure = loginFailure != null ? loginFailure
                : new LoginFailure(10, Duration.ofHours(24), Duration.ofMinutes(15));
        otpIssue = otpIssue != null ? otpIssue : new OtpIssue(Duration.ofSeconds(60), 5, 20, 10, 5);
    }

    /** 비운 값(max·기간)만 그 범위의 기본값으로 채운다. 설정한 max가 1 미만이면 기동에 실패한다(전부 차단 방지). */
    private static Window withDefaults(Window configured, Window defaults) {
        if (configured == null) {
            return defaults;
        }
        Integer max = configured.max() != null ? configured.max() : defaults.max();
        if (max < 1) {
            throw new IllegalArgumentException("member.limits.*.max는 1 이상이어야 한다");
        }
        Duration window = configured.window() != null ? configured.window() : defaults.window();
        return new Window(max, window);
    }

    public static MemberLimitProperties defaults() {
        return new MemberLimitProperties(null, null, null, null, null, null, null);
    }

    public Window window(RateLimitScope scope) {
        return switch (scope) {
            case SIGNUP -> signup;
            case LOGIN -> login;
            case OTP -> otp;
            case REFRESH -> refresh;
            case OAUTH_START -> oauthStart;
        };
    }

    /**
     * IP 기준 고정 창: 창 안에서 {@code max}번까지 허용. {@code max}는 설정에서 빠질 수 있도록 {@link Integer}다(빠지면 0으로
     * 바인딩되어 전부 차단되는 일을 막고 기본값을 채운다).
     */
    public record Window(Integer max, Duration window) {
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
