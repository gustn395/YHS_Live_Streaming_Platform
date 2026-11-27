package com.yhs.livestreaming.member.in;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OIDC 로그인 설정(MB-D023, MB-D024). Client ID·Secret은 기본값 없는 환경변수 {@code GOOGLE_CLIENT_ID}·
 * {@code GOOGLE_CLIENT_SECRET}, 공개 Base URL은 {@code MEMBER_OAUTH_PUBLIC_BASE_URL}로만 받고 없거나 형식이 틀리면 기동에
 * 실패한다. OTP·실패 이동 경로는 같은 출처의 고정 경로만 허용한다(외부 URL·스킴 상대 URL·쿼리·조각 금지). Google 엔드포인트는
 * 기본값이 Google이고 테스트만 가짜 공급자로 바꾼다.
 *
 * @param publicBaseUrl   브라우저가 보는 서비스 출처(예: {@code https://example.com}). Redirect URI를 만든다
 * @param otpRedirect     Google 1차 인증 성공 뒤 이동할 OTP 입력 화면 경로
 * @param failureRedirect 실패 이동 경로({@code ?error=<코드>}를 붙인다)
 */
@ConfigurationProperties("member.oauth")
public record MemberOAuthProperties(String publicBaseUrl, String otpRedirect, String failureRedirect, Google google) {

    public static final String START_PATH = "/api/v1/auth/oauth2/authorization/google";
    public static final String CALLBACK_PATH = "/api/v1/auth/oauth2/code/google";
    static final String AUTHORIZATION_BASE_URI = "/api/v1/auth/oauth2/authorization";
    static final String CALLBACK_BASE_URI = "/api/v1/auth/oauth2/code/*";

    public MemberOAuthProperties {
        publicBaseUrl = requireOrigin(publicBaseUrl);
        otpRedirect = otpRedirect == null ? "/login/otp" : requireLocalPath(otpRedirect, "otp-redirect");
        failureRedirect = failureRedirect == null ? "/login" : requireLocalPath(failureRedirect, "failure-redirect");
        if (google == null) {
            throw new IllegalArgumentException("member.oauth.google 설정이 없다(GOOGLE_CLIENT_ID·GOOGLE_CLIENT_SECRET)");
        }
    }

    public String redirectUri() {
        return publicBaseUrl + CALLBACK_PATH;
    }

    /**
     * @param clientId         {@code GOOGLE_CLIENT_ID}
     * @param clientSecret     {@code GOOGLE_CLIENT_SECRET}
     * @param authorizationUri 기본 Google 값. 테스트만 바꾼다
     * @param tokenUri         기본 Google 값
     * @param jwkSetUri        기본 Google 값
     * @param issuerUri        ID Token {@code iss} 검증 값. 기본 {@code https://accounts.google.com}
     */
    public record Google(String clientId, String clientSecret, String authorizationUri, String tokenUri,
                         String jwkSetUri, String issuerUri) {

        public Google {
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalArgumentException("GOOGLE_CLIENT_ID가 없다(기본값 없음)");
            }
            if (clientSecret == null || clientSecret.isBlank()) {
                throw new IllegalArgumentException("GOOGLE_CLIENT_SECRET이 없다(기본값 없음)");
            }
            clientId = clientId.strip();
            clientSecret = clientSecret.strip();
            // Token Endpoint에는 Client Secret이, JWKS에는 서명 검증 키가 오간다: https만(http는 loopback 테스트 공급자만)
            authorizationUri = requireSecureEndpoint(
                    orDefault(authorizationUri, "https://accounts.google.com/o/oauth2/v2/auth"), "authorization-uri");
            tokenUri = requireSecureEndpoint(orDefault(tokenUri, "https://www.googleapis.com/oauth2/v4/token"),
                    "token-uri");
            jwkSetUri = requireSecureEndpoint(orDefault(jwkSetUri, "https://www.googleapis.com/oauth2/v3/certs"),
                    "jwk-set-uri");
            issuerUri = orDefault(issuerUri, "https://accounts.google.com");
        }

        @Override
        public String toString() {
            return "Google[clientId=" + clientId + ", clientSecret=****, authorizationUri=" + authorizationUri
                    + ", tokenUri=" + tokenUri + ", jwkSetUri=" + jwkSetUri + ", issuerUri=" + issuerUri + "]";
        }

        private static String orDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.strip();
        }
    }

    private static String requireOrigin(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MEMBER_OAUTH_PUBLIC_BASE_URL이 없다(기본값 없음)");
        }
        String trimmed = value.strip();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("MEMBER_OAUTH_PUBLIC_BASE_URL 형식이 잘못됐다");
        }
        boolean http = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
        boolean bare = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) && uri.getRawQuery() == null
                && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        if (!http || uri.getHost() == null || !bare) {
            throw new IllegalArgumentException("MEMBER_OAUTH_PUBLIC_BASE_URL은 경로 없는 http(s) 출처여야 한다");
        }
        // __Host-yhs_oauth는 Secure 쿠키라 브라우저가 loopback 밖의 http 출처에서는 저장하지 않는다
        if (!httpsOrLoopbackHttp(uri)) {
            throw new IllegalArgumentException("MEMBER_OAUTH_PUBLIC_BASE_URL은 https여야 한다(http는 localhost·127.0.0.1·[::1]만)");
        }
        return trimmed;
    }

    private static String requireSecureEndpoint(String value, String name) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("member.oauth.google." + name + " 형식이 잘못됐다");
        }
        if (uri.getHost() == null || !httpsOrLoopbackHttp(uri)) {
            throw new IllegalArgumentException(
                    "member.oauth.google." + name + "는 https 절대 URL이어야 한다(http는 localhost·127.0.0.1·[::1]만)");
        }
        return value;
    }

    private static boolean httpsOrLoopbackHttp(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()) && LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private static String requireLocalPath(String value, String name) {
        boolean safe = value.startsWith("/") && !value.startsWith("//") && value.indexOf('\\') < 0
                && value.indexOf('?') < 0 && value.indexOf('#') < 0 && value.indexOf(':') < 0
                && value.chars().noneMatch(c -> c < 0x20 || c == 0x7F);
        if (!safe) {
            throw new IllegalArgumentException("member.oauth." + name + "는 같은 출처의 경로(/로 시작)여야 한다");
        }
        return value;
    }
}
