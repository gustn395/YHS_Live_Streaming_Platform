package com.yhs.livestreaming.member.app.password;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 비밀번호 구성 요소 등록. 키 설정이 잘못되면 Bean 생성에 실패해 애플리케이션이 뜨지 않는다(MB-D007).
 * 유출 비밀번호 검사 어댑터는 {@code out} 패키지에 있다(MB-D010).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemberPasswordProperties.class)
public class MemberPasswordConfig {

    @Bean
    Pbkdf2PasswordHasher pbkdf2PasswordHasher(MemberPasswordProperties properties) {
        return new Pbkdf2PasswordHasher(properties);
    }

    @Bean
    PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }
}
