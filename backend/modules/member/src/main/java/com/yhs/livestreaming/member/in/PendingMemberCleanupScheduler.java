package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.member.app.cleanup.MemberCleanupProperties;
import com.yhs.livestreaming.member.app.cleanup.PendingMemberCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

/**
 * 만료 PENDING 정리 자동 실행(MB-D030, G-D016). 고정 지연(fixed delay) 방식이고 첫 실행도 간격만큼 기다린다. 간격은 검증을 거친
 * {@link MemberCleanupProperties#interval()}에서 받아 기본값·검증을 한 곳에 둔다. {@code member.cleanup.enabled=false}면 이 빈이
 * 없어 자동으로 돌지 않는다(정리 서비스는 그대로 호출할 수 있다). 실패는 WARN으로 남기고 삼켜 다음 주기가 그대로 돈다(예외
 * 메시지는 남기지 않는다).
 */
@Component
@ConditionalOnProperty(prefix = "member.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PendingMemberCleanupScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(PendingMemberCleanupScheduler.class);

    private final PendingMemberCleanupService service;
    private final MemberCleanupProperties properties;

    PendingMemberCleanupScheduler(PendingMemberCleanupService service, MemberCleanupProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::run, properties.interval(), properties.interval()));
    }

    void run() {
        try {
            service.cleanupExpired();
        } catch (RuntimeException ex) {
            log.warn("만료 PENDING 정리 실행 실패: error={}", ex.getClass().getSimpleName());
        }
    }
}
