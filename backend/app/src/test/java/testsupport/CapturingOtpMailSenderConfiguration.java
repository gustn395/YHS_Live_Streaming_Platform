package testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 실제 SMTP 어댑터 대신 {@link CapturingOtpMailSender}를 주입한다(테스트는 실제 메일을 보내지 않는다). */
@TestConfiguration(proxyBeanMethods = false)
public class CapturingOtpMailSenderConfiguration {

    @Bean
    @Primary
    CapturingOtpMailSender capturingOtpMailSender() {
        return new CapturingOtpMailSender();
    }
}
