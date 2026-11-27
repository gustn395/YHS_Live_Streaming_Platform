package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.oauth.OAuthStateStore;
import com.yhs.livestreaming.member.app.session.RandomTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

/**
 * 세션 없는 OAuth 인증 요청 저장소(MB-D024). Spring이 시작 때 {@link #saveAuthorizationRequest}, 콜백 때
 * {@link #removeAuthorizationRequest}를 부른다(Security 6.5·7 공통 계약).
 * <ul>
 *     <li>저장: SecureRandom 256비트 id를 {@code __Host-yhs_oauth}(Lax) 쿠키로 주고, Redis 키는 id의 SHA-256이다(TTL 10분).
 *     PKCE verifier·nonce 등 Spring이 콜백에서 검증에 쓰는 필드를 문자열로 저장한다(Java 직렬화 없음). 저장소 장애면 요청 속성
 *     {@link #SAVE_FAILED}를 남기고, 시작 리다이렉트 전략이 Google 대신 실패 화면으로 보낸다.</li>
 *     <li>조회({@link #loadAuthorizationRequest}): 소비하지 않는다.</li>
 *     <li>제거(콜백): 쿠키 id와 요청 state가 모두 맞을 때만 원자적으로 읽고 지운다(1회). 소비했으면 {@link #CONSUMED}를 남겨 처리기가
 *     쿠키를 지운다. state가 다르면(이전 요청의 늦은 콜백) 아무것도 지우지 않는다. 저장소 장애는
 *     {@code temporarily_unavailable} 인증 오류다.</li>
 * </ul>
 */
public class OAuthCookieAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String SAVE_FAILED = OAuthCookieAuthorizationRequestRepository.class.getName() + ".SAVE_FAILED";
    public static final String CONSUMED = OAuthCookieAuthorizationRequestRepository.class.getName() + ".CONSUMED";
    public static final String STORE_UNAVAILABLE = "temporarily_unavailable";
    static final Duration TTL = Duration.ofMinutes(10);

    private static final String AUTHORIZATION_URI = "authorizationUri";
    private static final String CLIENT_ID = "clientId";
    private static final String REDIRECT_URI = "redirectUri";
    private static final String SCOPES = "scopes";
    private static final String STATE = "state";
    private static final String REQUEST_URI = "authorizationRequestUri";
    private static final String PARAMETER = "ap.";
    private static final String ATTRIBUTE = "at.";

    private final OAuthStateStore store;
    private final AuthCookieWriter cookies;

    public OAuthCookieAuthorizationRequestRepository(OAuthStateStore store, AuthCookieWriter cookies) {
        this.store = store;
        this.cookies = cookies;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Optional<String> id = cookie(request);
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (id.isEmpty() || state == null || !RandomTokens.isWellFormed(id.get())) {
            return null;
        }
        try {
            return store.find(RandomTokens.sha256Hex(id.get()), state).map(this::restore).orElse(null);
        } catch (LoginSessionStoreUnavailableException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error(STORE_UNAVAILABLE));
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            return;
        }
        String id = RandomTokens.newToken();
        try {
            store.save(RandomTokens.sha256Hex(id), flatten(authorizationRequest), TTL);
        } catch (LoginSessionStoreUnavailableException ex) {
            request.setAttribute(SAVE_FAILED, Boolean.TRUE);
            return;
        }
        cookies.writeOAuthState(response, id, TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        Optional<String> id = cookie(request);
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (id.isEmpty() || state == null || !RandomTokens.isWellFormed(id.get())) {
            return null;
        }
        Optional<Map<String, String>> consumed;
        try {
            consumed = store.consume(RandomTokens.sha256Hex(id.get()), state);
        } catch (LoginSessionStoreUnavailableException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error(STORE_UNAVAILABLE));
        }
        if (consumed.isEmpty()) {
            return null;
        }
        request.setAttribute(CONSUMED, Boolean.TRUE);
        return restore(consumed.get());
    }

    private static Optional<String> cookie(HttpServletRequest request) {
        Cookie[] all = request.getCookies();
        if (all == null) {
            return Optional.empty();
        }
        return Arrays.stream(all).filter(c -> MemberAuthCookies.OAUTH_STATE.equals(c.getName()))
                .map(Cookie::getValue).filter(v -> v != null && !v.isEmpty()).reduce((first, second) -> second);
    }

    private static Map<String, String> flatten(OAuth2AuthorizationRequest request) {
        Map<String, String> fields = new HashMap<>();
        fields.put(AUTHORIZATION_URI, request.getAuthorizationUri());
        fields.put(CLIENT_ID, request.getClientId());
        fields.put(REDIRECT_URI, request.getRedirectUri());
        fields.put(SCOPES, String.join(" ", request.getScopes()));
        fields.put(STATE, request.getState());
        fields.put(REQUEST_URI, request.getAuthorizationRequestUri());
        request.getAdditionalParameters().forEach((k, v) -> fields.put(PARAMETER + k, String.valueOf(v)));
        request.getAttributes().forEach((k, v) -> fields.put(ATTRIBUTE + k, String.valueOf(v)));
        return fields;
    }

    private OAuth2AuthorizationRequest restore(Map<String, String> fields) {
        Map<String, Object> parameters = new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        fields.forEach((k, v) -> {
            if (k.startsWith(PARAMETER)) {
                parameters.put(k.substring(PARAMETER.length()), v);
            } else if (k.startsWith(ATTRIBUTE)) {
                attributes.put(k.substring(ATTRIBUTE.length()), v);
            }
        });
        String scopes = fields.getOrDefault(SCOPES, "");
        Set<String> scopeSet = scopes.isBlank() ? Set.of() : new LinkedHashSet<>(Arrays.asList(scopes.split(" ")));
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(fields.get(AUTHORIZATION_URI))
                .clientId(fields.get(CLIENT_ID))
                .redirectUri(fields.get(REDIRECT_URI))
                .scopes(scopeSet)
                .state(fields.get(STATE))
                .additionalParameters(parameters)
                .attributes(attributes)
                .authorizationRequestUri(fields.get(REQUEST_URI))
                .build();
    }
}
