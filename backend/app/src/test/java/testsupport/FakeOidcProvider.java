package testsupport;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 테스트 전용 가짜 OIDC 공급자(MB-D023, 2라운드 Q10). JDK {@link HttpServer}로 token·JWKS 엔드포인트를 열고 ID Token을 RSA로
 * 서명한다. 실제 Google을 부르지 않는다. 인가 엔드포인트는 열지 않는다: 테스트가 시작 응답의 Location에서 state·nonce·PKCE
 * challenge를 읽어 {@link #authorize}로 code를 만들고 콜백을 직접 호출한다. token 엔드포인트는 code·redirect_uri·client 인증·
 * PKCE verifier(S256)를 확인한다.
 */
public final class FakeOidcProvider implements AutoCloseable {

    public static final String CLIENT_ID = "test-google-client";
    public static final String CLIENT_SECRET = "test-google-secret";

    /** 발급할 ID Token의 사용자 값. */
    public record GoogleUser(String sub, String email, Boolean emailVerified, String name) {
    }

    private record Grant(GoogleUser user, String nonce, String codeChallenge, String redirectUri,
                         Consumer<JWTClaimsSet.Builder> mutator) {
    }

    private final HttpServer server;
    private final RSAKey signingKey;
    private final RSAKey foreignKey;
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private volatile int tokenStatus = 200;
    private volatile boolean jwksAvailable = true;
    private volatile boolean signWithForeignKey;
    private volatile boolean rotatedKey;
    private final RSAKey rotated;

    public FakeOidcProvider() {
        try {
            signingKey = new RSAKeyGenerator(2048).keyID("fake-key-1").generate();
            foreignKey = new RSAKeyGenerator(2048).keyID("fake-key-1").generate();
            rotated = new RSAKeyGenerator(2048).keyID("fake-key-rotated").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/token", this::token);
        server.createContext("/jwks", this::jwks);
        server.start();
    }

    public String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String authorizationUri() {
        return issuer() + "/authorize";
    }

    public String tokenUri() {
        return issuer() + "/token";
    }

    public String jwkSetUri() {
        return issuer() + "/jwks";
    }

    /**
     * 시작 응답의 Location(인가 요청)을 받아 code를 발급하고, 콜백 쿼리({@code code=…&state=…})를 돌려준다.
     */
    public String authorize(String location, GoogleUser user) {
        return authorize(location, user, builder -> { });
    }

    public String authorize(String location, GoogleUser user, Consumer<JWTClaimsSet.Builder> idTokenMutator) {
        Map<String, String> query = query(URI.create(location).getRawQuery());
        String code = UUID.randomUUID().toString();
        grants.put(code, new Grant(user, query.get("nonce"), query.get("code_challenge"), query.get("redirect_uri"),
                idTokenMutator));
        return "code=" + code + "&state=" + encode(query.get("state"));
    }

    public static Map<String, String> query(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }

    public int tokenCalls() {
        return tokenCalls.get();
    }

    public void tokenStatus(int status) {
        this.tokenStatus = status;
    }

    public void jwksAvailable(boolean available) {
        this.jwksAvailable = available;
    }

    public void signWithForeignKey(boolean foreign) {
        this.signWithForeignKey = foreign;
    }

    /** 새 kid로 서명한다: 검증 쪽이 JWKS를 다시 가져오게 만든다(JWKS 장애 재현용). */
    public void rotateKey(boolean rotate) {
        this.rotatedKey = rotate;
    }

    public void reset() {
        rotatedKey = false;
        grants.clear();
        tokenCalls.set(0);
        tokenStatus = 200;
        jwksAvailable = true;
        signWithForeignKey = false;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenCalls.incrementAndGet();
        try (exchange) {
            if (tokenStatus != 200) {
                send(exchange, tokenStatus, "{\"error\":\"server_error\"}");
                return;
            }
            Map<String, String> form = query(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                    (CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
            Grant grant = grants.remove(form.getOrDefault("code", ""));
            if (!expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                send(exchange, 401, "{\"error\":\"invalid_client\"}");
                return;
            }
            if (grant == null || !"authorization_code".equals(form.get("grant_type"))
                    || !grant.redirectUri().equals(form.get("redirect_uri"))
                    || !s256(form.getOrDefault("code_verifier", "")).equals(grant.codeChallenge())) {
                send(exchange, 400, "{\"error\":\"invalid_grant\"}");
                return;
            }
            send(exchange, 200, "{\"access_token\":\"fake-access-" + UUID.randomUUID()
                    + "\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"openid email profile\","
                    + "\"id_token\":\"" + idToken(grant) + "\"}");
        }
    }

    private void jwks(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!jwksAvailable) {
                send(exchange, 503, "{}");
                return;
            }
            send(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
        }
    }

    private String idToken(Grant grant) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(issuer())
                    .audience(CLIENT_ID)
                    .subject(grant.user().sub())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300)))
                    .claim("nonce", grant.nonce());
            Map<String, Object> extra = new HashMap<>();
            if (grant.user().email() != null) {
                extra.put("email", grant.user().email());
            }
            if (grant.user().emailVerified() != null) {
                extra.put("email_verified", grant.user().emailVerified());
            }
            if (grant.user().name() != null) {
                extra.put("name", grant.user().name());
            }
            extra.forEach(claims::claim);
            grant.mutator().accept(claims);
            RSAKey key = rotatedKey ? rotated : signWithForeignKey ? foreignKey : signingKey;
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID())
                    .type(JOSEObjectType.JWT).build(), claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String s256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
