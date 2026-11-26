package com.yhs.livestreaming.global.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA 공통 설정(G-D010): 생성·수정 시각을 채우는 감사 기능을 켠다({@link BaseTimeEntity}). */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class GlobalJpaConfig {
}
