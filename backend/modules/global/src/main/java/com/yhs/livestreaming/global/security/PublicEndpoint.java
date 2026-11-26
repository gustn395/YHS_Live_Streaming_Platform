package com.yhs.livestreaming.global.security;

import org.springframework.http.HttpMethod;

/**
 * 인증 없이 허용할 경로 하나(HTTP 메서드 + Ant 스타일 패턴). 모듈은 {@link PublicEndpointContributor}로 등록한다(G-D004).
 *
 * @param method  허용할 HTTP 메서드
 * @param pattern 경로 패턴(예: {@code /api/v1/auth/signup})
 */
public record PublicEndpoint(HttpMethod method, String pattern) {

    public static PublicEndpoint of(HttpMethod method, String pattern) {
        return new PublicEndpoint(method, pattern);
    }
}
