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
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import com.yhs.livestreaming.global.auth.LoginSessionVerifier;
import com.yhs.livestreaming.global.security.csrf.SpaCsrfTokenRequestHandler;
import com.yhs.livestreaming.global.security.jwt.AccessTokenAuthenticationFilter;
import com.yhs.livestreaming.global.security.jwt.AccessTokenVerifier;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 앱 전체의 기본 보안 체인(G-D004, G-D005, G-D011).
 * <ul>
 *     <li>stateless: 세션을 만들지 않는다. 폼 로그인·HTTP Basic·로그아웃 필터는 쓰지 않는다.</li>
 *     <li>공개 경로: {@code /actuator/health}와 각 모듈이 {@link PublicEndpointContributor}로 등록한 경로. 나머지는 인증 필요.</li>
 *     <li>CSRF: SPA 이중 제출({@code XSRF-TOKEN} 쿠키 + {@code X-XSRF-TOKEN} 헤더)로 켠다(G-D014). 쿠키 인증은 이 보호가
 *     켜진 상태에서만 쓴다(G-D005).</li>
 *     <li>401·403은 ProblemDetail로 응답한다(G-D003).</li>
 * </ul>
 * Access 쿠키 인증 필터({@link AccessTokenAuthenticationFilter})는 익명 인증 필터 앞에 둔다(MB-D012).
 */
@Configuration(proxyBeanMethods = false)
public class GlobalSecurityConfig {

    @Bean
    SecurityFilterChain globalSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<PublicEndpointContributor> contributors,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            CookieCsrfTokenRepository csrfTokenRepository,
            AccessTokenVerifier accessTokenVerifier,
            LoginSessionVerifier loginSessionVerifier) throws Exception {
        List<PublicEndpoint> publicEndpoints = contributors.orderedStream()
                .flatMap(contributor -> contributor.publicEndpoints().stream())
                .toList();

        http
                // G-D014: SPA 이중 제출. 공개 경로(로그인·가입·OTP)의 상태 변경 요청도 보호한다(무시 경로 없음).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // stateless 체인에서는 Access 쿠키로 인증된 요청마다 기본 CsrfAuthenticationStrategy가 실행돼
                        // XSRF-TOKEN 삭제 쿠키를 보냈다. CSRF 토큰 갱신은 로그인 최종 성공·로그아웃에서 CsrfTokenRenewer로만
                        // 한다(G-D014, GL-T018).
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                // MB-D012·G-D007: Access 쿠키 인증. Bean으로 만들지 않고 여기서 한 번만 등록한다(서블릿 필터 중복 방지).
                .addFilterBefore(new AccessTokenAuthenticationFilter(
                                accessTokenVerifier, loginSessionVerifier, handlerExceptionResolver),
                        AnonymousAuthenticationFilter.class)
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
