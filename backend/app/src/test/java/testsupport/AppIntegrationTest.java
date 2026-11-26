package testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * app 통합 테스트 공통 설정: Testcontainers PostgreSQL + {@code test} 프로필(테스트 전용 키, application-test.yml)
 * + 유출 비밀번호 검사 대역(실제 HIBP 호출 없음). {@code @SpringBootTest}와 함께 붙인다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({PostgresTestcontainersConfiguration.class, StubBreachedPasswordCheckerConfiguration.class})
@ActiveProfiles("test")
public @interface AppIntegrationTest {
}
