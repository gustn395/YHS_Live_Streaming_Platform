package com.yhs.livestreaming.global.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화(G-D016). 앱 전체에 한 번만 켠다. 각 작업의 자동 실행 여부는 그 모듈의 설정값이 정하고(예:
 * {@code member.cleanup.enabled}), 단일 인스턴스 배포를 전제로 분산 잠금은 두지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class GlobalSchedulingConfig {
}
