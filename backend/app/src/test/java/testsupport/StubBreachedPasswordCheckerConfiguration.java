package testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 실제 HIBP 어댑터 대신 {@link StubBreachedPasswordChecker}를 주입한다(MB-D010: 테스트는 실제 HIBP를 부르지 않는다). */
@TestConfiguration(proxyBeanMethods = false)
public class StubBreachedPasswordCheckerConfiguration {

    @Bean
    @Primary
    StubBreachedPasswordChecker stubBreachedPasswordChecker() {
        return new StubBreachedPasswordChecker();
    }
}
