package com.yhs.livestreaming.global.security.jwt;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Access Token 발급·검증 구성(MB-D012). 키 설정이 잘못되면 Bean 생성에 실패해 애플리케이션이 뜨지 않는다(MB-D007).
 * 인증 필터는 Bean이 아니라 {@code GlobalSecurityConfig}가 보안 체인에 직접 등록한다(중복 등록 방지).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    JwtAccessTokenService jwtAccessTokenService(JwtProperties properties, AccessTokenRoles roles, Clock clock) {
        return new JwtAccessTokenService(properties, roles.allowedRoles(), clock);
    }
}
