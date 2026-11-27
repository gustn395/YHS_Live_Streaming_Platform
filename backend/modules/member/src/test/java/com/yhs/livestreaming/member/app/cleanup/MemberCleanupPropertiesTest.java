package com.yhs.livestreaming.member.app.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** MB-T063: 정리 배치 설정값(MB-D030). 기본값은 켜짐·1시간·500이고, 잘못된 값이면 기동에 실패한다. */
class MemberCleanupPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void defaultsAreEnabledHourlyAndFiveHundred() {
        runner.run(context -> assertThat(context.getBean(MemberCleanupProperties.class))
                .isEqualTo(new MemberCleanupProperties(true, Duration.ofHours(1), 500)));
    }

    @Test
    void valuesCanBeSet() {
        runner.withPropertyValues("member.cleanup.enabled=false", "member.cleanup.interval=10m",
                        "member.cleanup.batch-size=100")
                .run(context -> assertThat(context.getBean(MemberCleanupProperties.class))
                        .isEqualTo(new MemberCleanupProperties(false, Duration.ofMinutes(10), 100)));
    }

    @Test
    void invalidValuesFailStartup() {
        for (String invalid : new String[] {"member.cleanup.interval=0s", "member.cleanup.interval=-1m",
                "member.cleanup.batch-size=0", "member.cleanup.batch-size=10001"}) {
            runner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MemberCleanupProperties.class)
    static class PropertiesConfig {
    }
}
