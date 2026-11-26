package com.yhs.livestreaming.member.out.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OTP 메일 발신 주소(MB-D016). 기본값 없는 환경변수 {@code MEMBER_OTP_MAIL_FROM}으로 받는다. SMTP 접속 정보는 Boot 표준
 * {@code spring.mail.*}({@code SPRING_MAIL_HOST} 등)이고 이 저장소에는 기본값을 두지 않는다.
 *
 * @param from 보내는 주소
 */
@Validated
@ConfigurationProperties("member.otp.mail")
public record MemberOtpMailProperties(@NotBlank @Email String from) {
}
