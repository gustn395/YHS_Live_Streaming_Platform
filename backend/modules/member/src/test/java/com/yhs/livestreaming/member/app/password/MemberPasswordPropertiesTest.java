package com.yhs.livestreaming.member.app.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * MB-T008: pepper 키를 도입했으므로 키 누락·형식 오류·키 ID 누락이면 기동에 실패한다. 오류 메시지에 키 값을 넣지 않는다(MB-D007).
 */
class MemberPasswordPropertiesTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String SHORT_KEY = Base64.getEncoder().encodeToString(new byte[16]);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(MemberPasswordConfig.class));

    @Test
    void startsWithValidKey() {
        runner.withPropertyValues(
                        "member.password.pepper-key=" + VALID_KEY,
                        "member.password.pepper-key-id=k1")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(Pbkdf2PasswordHasher.class));
    }

    @Test
    void failsToStartWithoutKey() {
        runner.withPropertyValues("member.password.pepper-key-id=k1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWithoutKeyId() {
        runner.withPropertyValues("member.password.pepper-key=" + VALID_KEY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWhenKeyIsNot256Bits() {
        runner.withPropertyValues(
                        "member.password.pepper-key=" + SHORT_KEY,
                        "member.password.pepper-key-id=k1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonBase64KeyWithoutEchoingTheValue() {
        String notBase64 = "this-is-not-base64-%%%";

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MemberPasswordProperties(notBase64, "k1", 600_000))
                .withMessageNotContaining(notBase64);
    }

    @Test
    void rejectsShortKeyWithoutEchoingTheValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MemberPasswordProperties(SHORT_KEY, "k1", 600_000))
                .withMessageNotContaining(SHORT_KEY);
    }

    @Test
    void defaultsToSixHundredThousandIterations() {
        MemberPasswordProperties properties = new MemberPasswordProperties(VALID_KEY, "k1", null);

        assertThat(properties.iterations()).isEqualTo(600_000);
    }
}
