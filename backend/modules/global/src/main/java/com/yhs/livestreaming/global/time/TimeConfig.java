package com.yhs.livestreaming.global.time;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각 공급원. 만료·제한 계산은 이 {@link Clock}을 쓰고, 테스트는 고정·이동 가능한 Clock으로 바꾼다. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
