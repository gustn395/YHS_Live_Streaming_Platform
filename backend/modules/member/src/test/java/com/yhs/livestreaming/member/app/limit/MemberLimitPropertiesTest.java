package com.yhs.livestreaming.member.app.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 제한 설정 바인딩(MB-D017, MB-D018): 일부 값만 설정해도 나머지는 정책 기본값으로 채운다. */
class MemberLimitPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void noSettingsMeansPolicyDefaults() {
        runner.run(context -> {
            MemberLimitProperties limits = context.getBean(MemberLimitProperties.class);
            assertThat(limits.signup()).isEqualTo(new MemberLimitProperties.Window(5, Duration.ofHours(1)));
            assertThat(limits.login()).isEqualTo(new MemberLimitProperties.Window(30, Duration.ofMinutes(10)));
            assertThat(limits.otp()).isEqualTo(new MemberLimitProperties.Window(30, Duration.ofMinutes(10)));
        });
    }

    @Test
    void settingOnlyMaxKeepsTheScopeDefaultWindow() {
        runner.withPropertyValues("member.limits.signup.max=10", "member.limits.login.max=50",
                        "member.limits.otp.max=40")
                .run(context -> {
                    MemberLimitProperties limits = context.getBean(MemberLimitProperties.class);
                    assertThat(limits.signup()).isEqualTo(new MemberLimitProperties.Window(10, Duration.ofHours(1)));
                    assertThat(limits.login()).isEqualTo(new MemberLimitProperties.Window(50, Duration.ofMinutes(10)));
                    assertThat(limits.otp()).isEqualTo(new MemberLimitProperties.Window(40, Duration.ofMinutes(10)));
                });
    }

    @Test
    void settingBothMaxAndWindowKeepsBoth() {
        runner.withPropertyValues("member.limits.signup.max=7", "member.limits.signup.window=30m")
                .run(context -> assertThat(context.getBean(MemberLimitProperties.class).signup())
                        .isEqualTo(new MemberLimitProperties.Window(7, Duration.ofMinutes(30))));
    }

    @Test
    void settingOnlyWindowKeepsTheScopeDefaultMax() { // max가 빠지면 0(전부 차단)이 되지 않는다
        runner.withPropertyValues("member.limits.signup.window=30m", "member.limits.otp.window=5m")
                .run(context -> {
                    MemberLimitProperties limits = context.getBean(MemberLimitProperties.class);
                    assertThat(limits.signup()).isEqualTo(new MemberLimitProperties.Window(5, Duration.ofMinutes(30)));
                    assertThat(limits.otp()).isEqualTo(new MemberLimitProperties.Window(30, Duration.ofMinutes(5)));
                });
    }

    @Test
    void refreshDefaultsToOneHundredTwentyPerTenMinutesAndAcceptsPartialSettings() { // MB-T040, MB-D020
        runner.run(context -> {
            MemberLimitProperties limits = context.getBean(MemberLimitProperties.class);
            assertThat(limits.refresh()).isEqualTo(new MemberLimitProperties.Window(120, Duration.ofMinutes(10)));
            assertThat(limits.window(RateLimitScope.REFRESH)).isEqualTo(limits.refresh());
        });
        runner.withPropertyValues("member.limits.refresh.max=200")
                .run(context -> assertThat(context.getBean(MemberLimitProperties.class).refresh())
                        .isEqualTo(new MemberLimitProperties.Window(200, Duration.ofMinutes(10))));
        runner.withPropertyValues("member.limits.refresh.window=5m")
                .run(context -> assertThat(context.getBean(MemberLimitProperties.class).refresh())
                        .isEqualTo(new MemberLimitProperties.Window(120, Duration.ofMinutes(5))));
        runner.withPropertyValues("member.limits.refresh.max=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void maxBelowOneFailsStartup() {
        runner.withPropertyValues("member.limits.login.max=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MemberLimitProperties.class)
    static class PropertiesConfig {
    }
}
