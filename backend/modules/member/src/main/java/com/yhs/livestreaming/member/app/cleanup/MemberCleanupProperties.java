package com.yhs.livestreaming.member.app.cleanup;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 만료 PENDING 정리 설정(MB-D030). 비밀 값이 아니라 기본값을 둔다: 자동 실행 켜짐, 1시간(고정 지연·첫 실행 지연), 배치 500건.
 * 간격은 0보다 커야 하고 배치 크기는 1~10,000이며, 아니면 기동에 실패한다.
 */
@ConfigurationProperties("member.cleanup")
public record MemberCleanupProperties(Boolean enabled, Duration interval, Integer batchSize) {

    static final int MAX_BATCH_SIZE = 10_000;

    public MemberCleanupProperties {
        enabled = enabled == null || enabled;
        interval = interval == null ? Duration.ofHours(1) : interval;
        batchSize = batchSize == null ? 500 : batchSize;
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("member.cleanup.interval은 0보다 커야 한다");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("member.cleanup.batch-size는 1~" + MAX_BATCH_SIZE + "이어야 한다");
        }
    }
}
