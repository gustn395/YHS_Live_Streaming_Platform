package com.yhs.livestreaming.global.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** GL-T020: 스케줄링 활성화는 global 공통 설정이다(G-D016). 각 작업의 자동 실행 여부는 모듈 설정이 정한다. */
class GlobalSchedulingConfigTest {

    @Test
    void enablesScheduledAnnotationProcessing() {
        new ApplicationContextRunner().withUserConfiguration(GlobalSchedulingConfig.class)
                .run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
