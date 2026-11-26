package com.yhs.livestreaming.member.out.mail;

import com.yhs.livestreaming.member.app.otp.OtpMailSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * OTP 메일 발송 구성(MB-D016). {@link JavaMailSender}는 Boot가 {@code spring.mail.host}로 만든다. 호스트·발신 주소가 없으면
 * 기동에 실패한다(기본값 없음).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemberOtpMailProperties.class)
public class MemberMailConfig {

    @Bean
    OtpMailSender smtpOtpMailSender(JavaMailSender javaMailSender, MemberOtpMailProperties properties) {
        return new SmtpOtpMailSender(javaMailSender, properties);
    }
}
