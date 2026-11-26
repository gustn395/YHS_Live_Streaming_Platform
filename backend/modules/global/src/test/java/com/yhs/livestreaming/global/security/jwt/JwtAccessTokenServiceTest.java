package com.yhs.livestreaming.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * GL-T012: Access Token(JWT) 발급·검증(MB-D012). 외부 인프라 없이 실행한다.
 */
class JwtAccessTokenServiceTest {

    static final String KEY = "Cn8yJ3Yb7m0r1nC8wq6c0r0mP2aK0w1o5S8e3x6v9Qc="; // 테스트 전용 256비트
    static final String OTHER_KEY = "q0k6m3wXzP8b1N5c2V7e4R9t0Y3u6I1o8P5a2S7d4F0=";
    static final String SESSION_ID = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE"; // 43자 Base64url
    static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    static final Instant SESSION_EXPIRES = NOW.plus(Duration.ofDays(7));

    private final MutableClock clock = new MutableClock(NOW);
    private final JwtAccessTokenService service = service(KEY, "jwt-key-1");

    @Test
    void issuedTokenIsVerifiedWithItsClaims() {
        IssuedAccessToken token = service.issue(42L, "USER", SESSION_ID, SESSION_EXPIRES);

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(service.verify(token.value())).hasValueSatisfying(claims -> {
            assertThat(claims.memberId()).isEqualTo(42L);
            assertThat(claims.role()).isEqualTo("USER");
            assertThat(claims.sessionId()).isEqualTo(SESSION_ID);
            assertThat(claims.expiresAt()).isEqualTo(token.expiresAt());
        });
    }

    @Test
    void tokenCarriesOnlyAgreedClaimsAndHeader() {
        String[] parts = service.issue(42L, "USER", SESSION_ID, SESSION_EXPIRES).value().split("\\.");
        String header = decode(parts[0]);
        String payload = decode(parts[1]);

        assertThat(header).contains("\"alg\":\"HS256\"").contains("\"kid\":\"jwt-key-1\"");
        assertThat(payload).contains("\"iss\":\"yhs-live-streaming\"").contains("\"aud\":\"yhs-api\"")
                .contains("\"sub\":\"42\"").contains("\"sid\":\"" + SESSION_ID + "\"").contains("\"role\":\"USER\"")
                .contains("\"iat\":").contains("\"exp\":").doesNotContain("jti");
    }

    @Test
    void accessTokenNeverOutlivesTheLoginSession() {
        Instant sessionEndsSoon = NOW.plus(Duration.ofMinutes(3));

        IssuedAccessToken token = service.issue(42L, "USER", SESSION_ID, sessionEndsSoon);

        assertThat(token.expiresAt()).isEqualTo(sessionEndsSoon);
    }

    @Test
    void issuingForAnAlreadyEndedSessionIsRejected() {
        assertThatThrownBy(() -> service.issue(42L, "USER", SESSION_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenIsRejectedExactlyAtExpiryWithZeroClockSkew() {
        IssuedAccessToken token = service.issue(42L, "USER", SESSION_ID, SESSION_EXPIRES);

        clock.set(token.expiresAt().minusMillis(1));
        assertThat(service.verify(token.value())).isPresent();
        clock.set(token.expiresAt());
        assertThat(service.verify(token.value())).isEmpty();
    }

    @Test
    void tamperedSignatureIsRejected() {
        String value = service.issue(42L, "USER", SESSION_ID, SESSION_EXPIRES).value();
        char last = value.charAt(value.length() - 1);
        String tampered = value.substring(0, value.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(service.verify(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = service(OTHER_KEY, "jwt-key-1").issue(42L, "USER", SESSION_ID, SESSION_EXPIRES).value();

        assertThat(service.verify(foreign)).isEmpty();
    }

    @Test
    void tokenWithDifferentKeyIdIsRejected() {
        String otherKid = service(KEY, "jwt-key-2").issue(42L, "USER", SESSION_ID, SESSION_EXPIRES).value();

        assertThat(service.verify(otherKid)).isEmpty();
    }

    @Test
    void otherAlgorithmsAreRejected() {
        // HS512는 512비트 이상 키가 필요하다. 같은 키를 두 번 이어 붙여 서명만 만들 수 있게 한다.
        byte[] key = Base64.getDecoder().decode(KEY);
        byte[] longKey = new byte[64];
        System.arraycopy(key, 0, longKey, 0, 32);
        System.arraycopy(key, 0, longKey, 32, 32);
        assertThat(service.verify(craft(longKey, MacAlgorithm.HS512, "jwt-key-1", c -> { }))).isEmpty();
        String unsigned = base64Url("{\"alg\":\"none\",\"kid\":\"jwt-key-1\"}") + "."
                + craft(MacAlgorithm.HS256, "jwt-key-1", c -> { }).split("\\.")[1] + ".";
        assertThat(service.verify(unsigned)).isEmpty();
    }

    @Test
    void wrongIssuerOrAudienceIsRejected() {
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.issuer("someone-else")))).isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.audience(List.of("other-api")))))
                .isEmpty();
    }

    @Test
    void missingOrMalformedRequiredClaimsAreRejected() {
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.claims(m -> m.remove("sid")))))
                .isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.claims(m -> m.remove("exp")))))
                .isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.claims(m -> m.remove("iat")))))
                .isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.claim("sid", "short")))).isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.claim("sid", 12345)))).isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.subject("abc")))).isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.subject("0")))).isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.subject("-7")))).isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.claim("role", "ROOT")))).isEmpty();
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> c.claim("role", 1)))).isEmpty();
    }

    @Test
    void craftedValidTokenPassesSoTheNegativeCasesAreMeaningful() {
        assertThat(service.verify(craft(MacAlgorithm.HS256, "jwt-key-1", c -> { }))).isPresent();
    }

    @Test
    void garbageIsRejectedWithoutException() {
        assertThat(service.verify("not-a-jwt")).isEmpty();
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify(null)).isEmpty();
    }

    private JwtAccessTokenService service(String key, String keyId) {
        return new JwtAccessTokenService(new JwtProperties(key, keyId), Set.of("USER", "ADMIN"), clock);
    }

    /** 같은 키로 서명하되 Claim·헤더를 바꾼 토큰. 기본값은 유효한 토큰이다. */
    private static String craft(MacAlgorithm algorithm, String keyId, Consumer<JwtClaimsSet.Builder> customizer) {
        return craft(Base64.getDecoder().decode(KEY), algorithm, keyId, customizer);
    }

    private static String craft(byte[] key, MacAlgorithm algorithm, String keyId,
                                Consumer<JwtClaimsSet.Builder> customizer) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(new SecretKeySpec(key, "HmacSHA256")).keyID(keyId).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("yhs-live-streaming")
                .audience(List.of("yhs-api"))
                .subject("42")
                .claim("sid", SESSION_ID)
                .claim("role", "USER")
                .issuedAt(NOW)
                .expiresAt(NOW.plus(Duration.ofMinutes(15)));
        customizer.accept(claims);
        JwsHeader header = JwsHeader.with(algorithm).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
