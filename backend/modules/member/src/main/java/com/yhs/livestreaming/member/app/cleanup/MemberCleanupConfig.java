package com.yhs.livestreaming.member.app.cleanup;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 만료 PENDING 정리 설정 등록(MB-D030). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemberCleanupProperties.class)
class MemberCleanupConfig {
}
