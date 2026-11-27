package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.member.app.limit.RequestRateLimiter;
import com.yhs.livestreaming.member.app.oauth.GoogleLoginService;
import com.yhs.livestreaming.member.app.oauth.OAuthFailure;
import com.yhs.livestreaming.member.app.oauth.OAuthStateStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.client.RestClient;

/**
 * Google OIDC 로그인 전용 보안 체인(MB-D023, MB-D024). global 체인보다 먼저 적용되고 {@code GET} 시작·콜백 두 경로만 맡는다.
 * 그 밖의 OAuth 하위 경로·메서드는 이 체인에 걸리지 않아 global 체인(인증 필요)으로 간다.
 * <ul>
 *     <li>세션·RequestCache 없음, SecurityContext 저장 없음({@link NullSecurityContextRepository}), AuthorizedClient 저장 없음.</li>
 *     <li>CSRF는 끈다: 두 경로는 최상위 GET 이동이고, 로그인 CSRF는 state + {@code __Host-yhs_oauth} 쿠키 묶음으로 막는다.</li>
 *     <li>보안 헤더는 Spring Security 기본값(global 체인과 같음)을 그대로 쓴다.</li>
 *     <li>PKCE(S256)를 기밀 클라이언트에도 켠다. nonce는 openid scope로 Spring이 붙이고 ID Token에서 검증한다.</li>
 *     <li>Google 성공은 1차 인증일 뿐이다: 성공 처리기가 OTP만 시작하고 로그인 세션·인증 쿠키는 만들지 않는다.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemberOAuthProperties.class)
public class MemberOAuthSecurityConfig {

    static final Duration GOOGLE_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration GOOGLE_READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    ClientRegistrationRepository googleClientRegistrationRepository(MemberOAuthProperties properties) {
        MemberOAuthProperties.Google google = properties.google();
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId(google.clientId())
                .clientSecret(google.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.redirectUri())
                .scope("openid", "email", "profile")
                .authorizationUri(google.authorizationUri())
                .tokenUri(google.tokenUri())
                .jwkSetUri(google.jwkSetUri())
                .issuerUri(google.issuerUri())
                // UserInfo 엔드포인트는 부르지 않는다: 필요한 값은 검증된 ID Token에 있다(Google 토큰 사용 최소화)
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Google")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    SecurityFilterChain memberOAuthFilterChain(HttpSecurity http, MemberOAuthProperties properties,
                                               ClientRegistrationRepository registrations, OAuthStateStore stateStore,
                                               AuthCookieWriter cookies, GoogleLoginService googleLoginService,
                                               RequestRateLimiter rateLimiter) throws Exception {
        OAuthRedirects redirects = new OAuthRedirects(properties);
        OAuthCookieAuthorizationRequestRepository requests =
                new OAuthCookieAuthorizationRequestRepository(stateStore, cookies);
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, MemberOAuthProperties.AUTHORIZATION_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        http
                .securityMatcher(new OrRequestMatcher(
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, MemberOAuthProperties.START_PATH),
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,
                                MemberOAuthProperties.CALLBACK_PATH)))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint
                                .baseUri(MemberOAuthProperties.AUTHORIZATION_BASE_URI)
                                .authorizationRequestRepository(requests)
                                .authorizationRequestResolver(resolver)
                                .authorizationRedirectStrategy(startRedirect(redirects)))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri(MemberOAuthProperties.CALLBACK_BASE_URI))
                        .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClient()))
                        .authorizedClientRepository(new NoStoreAuthorizedClientRepository())
                        .successHandler(new GoogleLoginSuccessHandler(googleLoginService, cookies, redirects))
                        .failureHandler(new GoogleLoginFailureHandler(cookies, redirects)))
                // Bean이 아니라 new로 이 체인에만 한 번 등록한다(서블릿 필터 중복 등록 방지)
                .addFilterBefore(new OAuthRateLimitFilter(rateLimiter, redirects),
                        OAuth2AuthorizationRequestRedirectFilter.class);
        return http.build();
    }

    /** 임시 상태를 저장하지 못했으면 Google 대신 실패 화면으로 보낸다(쿠키도 주지 않았다). */
    private static RedirectStrategy startRedirect(OAuthRedirects redirects) {
        DefaultRedirectStrategy google = new DefaultRedirectStrategy();
        return (HttpServletRequest request, HttpServletResponse response, String url) -> {
            if (Boolean.TRUE.equals(request.getAttribute(OAuthCookieAuthorizationRequestRepository.SAVE_FAILED))) {
                redirects.toFailure(response, OAuthFailure.TEMPORARILY_UNAVAILABLE);
                return;
            }
            google.sendRedirect(request, response, url);
        };
    }

    /** Google Token Endpoint 호출에 연결·읽기 시간 제한을 둔다. 시간 초과는 통신 장애로 분류된다. */
    private static RestClientAuthorizationCodeTokenResponseClient tokenClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(GOOGLE_CONNECT_TIMEOUT);
        factory.setReadTimeout(GOOGLE_READ_TIMEOUT);
        RestClient restClient = RestClient.builder()
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .requestFactory(factory)
                .build();
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(restClient);
        return client;
    }

    /** Google Access·Refresh Token을 어디에도 남기지 않는다(MB-D023). */
    static final class NoStoreAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
                                                                         Authentication principal,
                                                                         HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                                         HttpServletRequest request, HttpServletResponse response) {
            // 저장하지 않는다
        }

        @Override
        public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                                           HttpServletRequest request, HttpServletResponse response) {
            // 저장한 것이 없다
        }
    }
}
