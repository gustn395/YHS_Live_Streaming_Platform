package com.yhs.livestreaming.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * GL-T016: JWT 서명 키 기동 검증(MB-D007, MB-D012). 키가 없거나 형식이 틀리면 기동에 실패하고, 오류 메시지에 키 값이 없다.
 */
class JwtPropertiesTest {

    private static final String KEY = JwtAccessTokenServiceTest.KEY;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void validKeyBinds() {
        runner.withPropertyValues("auth.jwt.key=" + KEY, "auth.jwt.key-id=jwt-key-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(JwtProperties.class).keyBytes()).hasSize(32);
                    assertThat(context.getBean(JwtProperties.class).toString()).doesNotContain(KEY);
                });
    }

    @Test
    void missingKeyFailsStartup() {
        runner.withPropertyValues("auth.jwt.key-id=jwt-key-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingKeyIdFailsStartup() {
        runner.withPropertyValues("auth.jwt.key=" + KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidKeyIdFailsStartup() {
        runner.withPropertyValues("auth.jwt.key=" + KEY, "auth.jwt.key-id=bad id!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void nonBase64KeyFailsWithoutLeakingTheValue() {
        String secretLooking = "not*base64*secret-value";
        runner.withPropertyValues("auth.jwt.key=" + secretLooking, "auth.jwt.key-id=jwt-key-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootMessage(context.getStartupFailure())).doesNotContain(secretLooking);
                });
    }

    @Test
    void keyThatIsNot256BitsFailsWithoutLeakingTheValue() {
        String shortKey = "c2hvcnQta2V5"; // 9바이트
        runner.withPropertyValues("auth.jwt.key=" + shortKey, "auth.jwt.key-id=jwt-key-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(allMessages(context.getStartupFailure())).doesNotContain(shortKey).contains("256");
                });
    }

    private static String rootMessage(Throwable failure) {
        return allMessages(failure);
    }

    private static String allMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {
    }
}
