package testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * app 통합 테스트용 Redis(Testcontainers, G-D009). 로그인 세션·OTP·요청 제한 저장소(MB-D014~D019)를 실제 Redis로 검증한다.
 * {@code @ServiceConnection(name = "redis")}가 Redis 접속 정보를 컨테이너로 바꾼다. 로컬 실행에는 Docker가 필요하다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainersConfiguration {

    public static final String IMAGE = "redis:7.4-alpine";

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(IMAGE).withExposedPorts(6379);
    }
}
