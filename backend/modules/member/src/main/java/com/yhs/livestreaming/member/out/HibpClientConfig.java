package com.yhs.livestreaming.member.out;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** HIBP 어댑터 설정 등록(MB-D010). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HibpProperties.class)
class HibpClientConfig {
}
