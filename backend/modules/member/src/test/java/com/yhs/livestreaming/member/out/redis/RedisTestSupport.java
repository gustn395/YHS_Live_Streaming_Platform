package com.yhs.livestreaming.member.out.redis;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * member Redis 어댑터 테스트 공통: 실제 Redis(Testcontainers, G-D009). Lua 원자성·TTL·동시성을 운영과 같은 Redis로 검증한다.
 *
 * <p>컨테이너는 JVM에 하나만 띄운다(싱글턴). 클래스마다 {@code @Container}로 재시작하면 캐시된 Spring 테스트 컨텍스트가
 * 이전 컨테이너 포트를 계속 써서 연결이 끊긴다. 테스트마다 DB를 비운다.
 */
abstract class RedisTestSupport {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        redis.start();
    }

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void flushRedis() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
