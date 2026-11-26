package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.global.security.jwt.AccessTokenIssuer;
import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.limit.LoginAttemptLimiter;
import com.yhs.livestreaming.member.app.limit.MemberLimitProperties;
import com.yhs.livestreaming.member.app.otp.MemberOtpProperties;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStore;
import com.yhs.livestreaming.member.app.otp.OtpCodes;
import com.yhs.livestreaming.member.app.otp.OtpMailSender;
import com.yhs.livestreaming.member.app.otp.OtpService;
import com.yhs.livestreaming.member.app.password.DummyPasswordHash;
import com.yhs.livestreaming.member.app.password.PasswordHasher;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.RandomTokens;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 로그인·OTP·세션 발급 구성(MB-W05~W08). OTP HMAC 키가 잘못되면 기동에 실패한다(MB-D007).
 * 저장소 어댑터(Redis)와 메일 어댑터(SMTP)는 {@code out} 패키지가 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MemberLimitProperties.class, MemberOtpProperties.class})
public class MemberLoginConfig {

    @Bean
    DummyPasswordHash dummyPasswordHash(PasswordHasher passwordHasher) {
        return DummyPasswordHash.create(passwordHasher);
    }

    @Bean
    OtpCodes otpCodes(MemberOtpProperties properties) {
        return new OtpCodes(properties);
    }

    @Bean
    OtpService otpService(OtpChallengeStore store, OtpCodes codes, OtpMailSender mailSender,
                          MemberRepository members, MemberLimitProperties limits, Clock clock) {
        return new OtpService(store, codes, mailSender, members::findById, clock, RandomTokens::newToken,
                limits.otpIssue().interval());
    }

    @Bean
    LoginService loginService(MemberRepository members, PasswordHasher passwordHasher, DummyPasswordHash dummyHash,
                              LoginAttemptLimiter attempts, OtpService otpService, Clock clock) {
        return new LoginService(members, passwordHasher, dummyHash, attempts, otpService, clock);
    }

    @Bean
    LoginCompletionService loginCompletionService(MemberRepository members, LoginSessionStore sessions,
                                                  AccessTokenIssuer accessTokenIssuer, Clock clock) {
        return new LoginCompletionService(members, sessions, accessTokenIssuer, clock);
    }
}
