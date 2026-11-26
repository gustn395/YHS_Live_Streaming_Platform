package com.yhs.livestreaming.global.security;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 앱 전체의 기본 보안 체인(G-D004, G-D005, G-D011).
 * <ul>
 *     <li>stateless: 세션을 만들지 않는다. 폼 로그인·HTTP Basic·로그아웃 필터는 쓰지 않는다.</li>
 *     <li>공개 경로: {@code /actuator/health}와 각 모듈이 {@link PublicEndpointContributor}로 등록한 경로. 나머지는 인증 필요.</li>
 *     <li>CSRF: 쿠키 기반 인증이 없는 지금 단계에서만 끈다(G-D005). 쿠키 인증을 넣는 MB-W05에서 켜야 한다(G-U02).</li>
 *     <li>401·403은 ProblemDetail로 응답한다(G-D003).</li>
 * </ul>
 * JWT 검증 필터는 MB-W05에서 이 체인에 더한다.
 */
@Configuration(proxyBeanMethods = false)
public class GlobalSecurityConfig {

    @Bean
    SecurityFilterChain globalSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<PublicEndpointContributor> contributors,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) throws Exception {
        List<PublicEndpoint> publicEndpoints = contributors.orderedStream()
                .flatMap(contributor -> contributor.publicEndpoints().stream())
                .toList();

        http
                .csrf(AbstractHttpConfigurer::disable) // G-D005: 쿠키 인증 도입 전 한정
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint(handlerExceptionResolver))
                        .accessDeniedHandler(new ProblemDetailAccessDeniedHandler(handlerExceptionResolver)))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll();
                    for (PublicEndpoint endpoint : publicEndpoints) {
                        authorize.requestMatchers(endpoint.method(), endpoint.pattern()).permitAll();
                    }
                    authorize.anyRequest().authenticated();
                });
        return http.build();
    }
}
