package com.yhs.livestreaming.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.yhs.livestreaming.member.app.cleanup.CleanupResult;
import com.yhs.livestreaming.member.app.cleanup.MemberCleanupProperties;
import com.yhs.livestreaming.member.app.cleanup.PendingMemberCleanupService;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/** MB-T063: 정리 스케줄러 — 자동 실행은 설정으로 끄고, 실패해도 다음 주기를 막지 않는다(MB-D030, G-D016). */
@ExtendWith(OutputCaptureExtension.class)
class PendingMemberCleanupSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceConfig.class, PendingMemberCleanupScheduler.class);

    @Test
    void schedulerExistsByDefaultAndIsOffWhenDisabled() {
        runner.run(context -> assertThat(context).hasSingleBean(PendingMemberCleanupScheduler.class));
        runner.withPropertyValues("member.cleanup.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PendingMemberCleanupScheduler.class));
    }

    @Test
    void registersAFixedDelayTaskUsingTheValidatedInterval() { // 기본값·검증은 MemberCleanupProperties 한 곳
        PendingMemberCleanupScheduler scheduler = new PendingMemberCleanupScheduler(null,
                new MemberCleanupProperties(true, Duration.ofMinutes(10), 500));
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        scheduler.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement().satisfies(task -> {
            assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(10));
            assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofMinutes(10));
        });
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
        assertThat(registrar.getCronTaskList()).isEmpty();
    }

    @Test
    void failureIsLoggedAndSwallowedSoTheNextRunHappens(CapturedOutput output) {
        AtomicInteger calls = new AtomicInteger();
        PendingMemberCleanupScheduler scheduler = new PendingMemberCleanupScheduler(new PendingMemberCleanupService(
                null, null, null) {
            @Override
            public CleanupResult cleanupExpired() {
                calls.incrementAndGet();
                throw new IllegalStateException("db down tester@example.com");
            }
        }, new MemberCleanupProperties(true, Duration.ofHours(1), 500));

        assertThatCode(scheduler::run).doesNotThrowAnyException();
        assertThatCode(scheduler::run).doesNotThrowAnyException();

        assertThat(calls).hasValue(2);
        assertThat(output.getAll()).contains("WARN").contains("IllegalStateException")
                .doesNotContain("tester@example.com");
    }

    @Configuration(proxyBeanMethods = false)
    static class ServiceConfig {
        @Bean
        MemberCleanupProperties memberCleanupProperties() {
            return new MemberCleanupProperties(true, Duration.ofHours(1), 500);
        }

        @Bean
        PendingMemberCleanupService pendingMemberCleanupService() {
            return new PendingMemberCleanupService(null, null, null) {
                @Override
                public CleanupResult cleanupExpired() {
                    return new CleanupResult(0, 0, true, Duration.ZERO);
                }
            };
        }
    }
}
