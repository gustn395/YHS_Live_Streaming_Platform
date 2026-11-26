package com.yhs.livestreaming.member.out;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HIBP Pwned Passwords range API 설정(MB-D010). 비밀 값이 없어 기본값을 둔다.
 *
 * @param baseUrl        API 기준 주소(기본 {@code https://api.pwnedpasswords.com})
 * @param connectTimeout 연결 시간 제한(기본 1초)
 * @param readTimeout    응답 시간 제한(기본 2초)
 */
@ConfigurationProperties("member.password.hibp")
public record HibpProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {

    static final URI DEFAULT_BASE_URL = URI.create("https://api.pwnedpasswords.com");
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(1);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(2);

    public HibpProperties {
        if (baseUrl == null) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("member.password.hibp 시간 제한은 0보다 커야 한다");
        }
    }
}
