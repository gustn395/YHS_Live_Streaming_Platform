package com.yhs.livestreaming.member.in;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** MB-T052: Google 자격 증명·공개 Base URL·고정 이동 경로 기동 검증(MB-D023, MB-D024). */
class MemberOAuthPropertiesTest {

    private static final String[] VALID = {
            "member.oauth.public-base-url=https://app.example.com",
            "member.oauth.google.client-id=client-id",
            "member.oauth.google.client-secret=client-secret"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void validSettingsUseDefaultRedirectsAndGoogleEndpoints() {
        runner.withPropertyValues(VALID).run(context -> {
            assertThat(context).hasNotFailed();
            MemberOAuthProperties props = context.getBean(MemberOAuthProperties.class);
            assertThat(props.otpRedirect()).isEqualTo("/login/otp");
            assertThat(props.failureRedirect()).isEqualTo("/login");
            assertThat(props.google().issuerUri()).isEqualTo("https://accounts.google.com");
            assertThat(props.redirectUri()).isEqualTo("https://app.example.com/api/v1/auth/oauth2/code/google");
            assertThat(props.toString()).doesNotContain("client-secret");
        });
    }

    @Test
    void missingOrBlankCredentialsFailStartup() {
        runner.withPropertyValues(VALID[0], VALID[2]).run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(VALID[0], VALID[1]).run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(VALID[0], "member.oauth.google.client-id= ", VALID[2])
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void publicBaseUrlMustBeAnAbsoluteHttpOriginWithoutPathQueryOrUserInfo() {
        for (String bad : new String[] {"", "app.example.com", "ftp://app.example.com", "https://app.example.com/x",
                "https://app.example.com?x=1", "https://user@app.example.com", "//app.example.com"}) {
            runner.withPropertyValues("member.oauth.public-base-url=" + bad, VALID[1], VALID[2])
                    .run(context -> assertThat(context).as(bad).hasFailed());
        }
        runner.withPropertyValues("member.oauth.public-base-url=http://localhost:8080", VALID[1], VALID[2])
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void plainHttpIsAllowedOnlyForLoopbackHosts() { // __Host- 쿠키는 Secure라 http 운영 출처에서는 저장되지 않는다
        for (String bad : new String[] {"http://app.example.com", "http://10.0.0.5:8080", "http://localhost.evil.com"}) {
            runner.withPropertyValues("member.oauth.public-base-url=" + bad, VALID[1], VALID[2])
                    .run(context -> assertThat(context).as(bad).hasFailed());
        }
        for (String ok : new String[] {"http://localhost:8080", "http://127.0.0.1:5173", "http://[::1]:8080",
                "https://app.example.com"}) {
            runner.withPropertyValues("member.oauth.public-base-url=" + ok, VALID[1], VALID[2])
                    .run(context -> assertThat(context).as(ok).hasNotFailed());
        }
    }

    @Test
    void fixedRedirectsMustBeSafeSameOriginPaths() {
        for (String bad : new String[] {"https://evil.example/", "//evil.example", "/\\evil.example", "login",
                "javascript:alert(1)", "/login?x=1", "/login#x", "/log\nin"}) {
            runner.withPropertyValues(VALID).withPropertyValues("member.oauth.failure-redirect=" + bad)
                    .run(context -> assertThat(context).as(bad).hasFailed());
            runner.withPropertyValues(VALID).withPropertyValues("member.oauth.otp-redirect=" + bad)
                    .run(context -> assertThat(context).as(bad).hasFailed());
        }
        runner.withPropertyValues(VALID).withPropertyValues("member.oauth.otp-redirect=/auth/otp")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MemberOAuthProperties.class)
    static class PropertiesConfig {
    }
}
