package com.yhs.livestreaming.global.security.csrf;

import com.yhs.livestreaming.global.security.PublicEndpoint;
import com.yhs.livestreaming.global.security.PublicEndpointContributor;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * CSRF 구성 요소(G-D014). 토큰은 {@code XSRF-TOKEN} 쿠키(HttpOnly 아님, Secure, SameSite=Strict, Path=/, Domain 미지정)와
 * {@code X-XSRF-TOKEN} 헤더로 주고받는다. 보안 체인 등록은 {@code GlobalSecurityConfig}가 한다.
 */
@Configuration(proxyBeanMethods = false)
public class CsrfConfig {

    public static final String CSRF_ENDPOINT = "/api/v1/auth/csrf";

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict"));
        return repository;
    }

    @Bean
    CsrfTokenRenewer csrfTokenRenewer(CookieCsrfTokenRepository csrfTokenRepository) {
        return new CsrfTokenRenewer(csrfTokenRepository);
    }

    @Bean
    PublicEndpointContributor csrfPublicEndpoints() {
        return () -> List.of(PublicEndpoint.of(HttpMethod.GET, CSRF_ENDPOINT));
    }
}
