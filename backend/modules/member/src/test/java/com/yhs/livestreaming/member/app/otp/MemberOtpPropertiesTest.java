package com.yhs.livestreaming.member.app.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.yhs.livestreaming.member.out.mail.MemberOtpMailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * MB-T033: OTP HMAC 키·메일 발신자 기동 검증(MB-D007, MB-D016). 누락·형식 오류면 기동에 실패하고 메시지에 키 값이 없다.
 */
class MemberOtpPropertiesTest {

    private static final String KEY = OtpServiceTest.OTP_KEY;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues("member.otp.mail.from=no-reply@example.com");

    @Test
    void validKeyBinds() {
        runner.withPropertyValues("member.otp.hmac-key=" + KEY, "member.otp.hmac-key-id=otp-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MemberOtpProperties.class).toString()).doesNotContain(KEY);
                });
    }

    @Test
    void missingKeyOrKeyIdFailsStartup() {
        runner.withPropertyValues("member.otp.hmac-key-id=otp-1").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("member.otp.hmac-key=" + KEY).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void malformedKeyFailsWithoutLeakingTheValue() {
        String notBase64 = "*not-base64-secret*";
        String shortKey = "c2hvcnQta2V5";
        runner.withPropertyValues("member.otp.hmac-key=" + notBase64, "member.otp.hmac-key-id=otp-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(messages(context.getStartupFailure())).doesNotContain(notBase64);
                });
        runner.withPropertyValues("member.otp.hmac-key=" + shortKey, "member.otp.hmac-key-id=otp-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(messages(context.getStartupFailure())).doesNotContain(shortKey).contains("256");
                });
    }

    @Test
    void missingOrInvalidMailSenderAddressFailsStartup() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("member.otp.hmac-key=" + KEY, "member.otp.hmac-key-id=otp-1")
                .run(context -> assertThat(context).hasFailed());
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("member.otp.hmac-key=" + KEY, "member.otp.hmac-key-id=otp-1",
                        "member.otp.mail.from=not-an-address")
                .run(context -> assertThat(context).hasFailed());
    }

    private static String messages(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            text.append(t.getMessage()).append('\n');
        }
        return text.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({MemberOtpProperties.class, MemberOtpMailProperties.class})
    static class PropertiesConfig {
    }
}
