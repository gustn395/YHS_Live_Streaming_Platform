package com.yhs.livestreaming.global.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Access Token(JWT) 발급·검증(MB-D012). Spring Security 6.5에도 있는 Nimbus 인코더·디코더 API만 쓴다.
 *
 * <ul>
 *     <li>헤더 {@code alg=HS256}·{@code kid}. 본문 {@code iss}·{@code aud}·{@code sub}(memberId 문자열)·{@code sid}·
 *     {@code role}·{@code iat}·{@code exp}. 개인정보와 {@code jti}는 넣지 않는다.</li>
 *     <li>검증: 알고리즘(HS256만)·서명·{@code kid}·{@code iss}·{@code aud}·필수 Claim의 존재와 타입·{@code role} 허용 값·
 *     {@code sub} 양수. 시계 오차 0초이고 {@code exp} 시각과 같으면 만료다(RFC 7519 "on or after").</li>
 * </ul>
 * 검증 실패 이유는 밖으로 내보내지 않는다(토큰 값이 로그·응답에 남지 않게).
 */
public class JwtAccessTokenService implements AccessTokenIssuer, AccessTokenVerifier {

    public static final String ISSUER = "yhs-live-streaming";
    public static final String AUDIENCE = "yhs-api";
    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    static final String SESSION_ID_CLAIM = "sid";
    static final String ROLE_CLAIM = "role";

    private static final Pattern SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String keyId;
    private final Set<String> allowedRoles;
    private final Clock clock;

    public JwtAccessTokenService(JwtProperties properties, Set<String> allowedRoles, Clock clock) {
        this.keyId = properties.keyId();
        this.allowedRoles = Set.copyOf(allowedRoles);
        this.clock = Objects.requireNonNull(clock, "clock");
        SecretKey secretKey = new SecretKeySpec(properties.keyBytes(), "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey).keyID(keyId).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
        this.decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> requiredClaims = this::validateRequiredClaims;
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                timestamps, new JwtIssuerValidator(ISSUER), requiredClaims)));
    }

    @Override
    public IssuedAccessToken issue(long memberId, String role, String sessionId, Instant sessionExpiresAt) {
        if (memberId <= 0 || !allowedRoles.contains(role) || sessionId == null
                || !SESSION_ID.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("Access Token 발급 값이 잘못됐다");
        }
        Instant now = clock.instant();
        if (sessionExpiresAt == null || !now.isBefore(sessionExpiresAt)) {
            throw new IllegalArgumentException("이미 끝난 로그인 세션이다");
        }
        Instant expiresAt = min(now.plus(ACCESS_TOKEN_TTL), sessionExpiresAt);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(Long.toString(memberId))
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(ROLE_CLAIM, role)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).keyId(keyId).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, expiresAt);
    }

    /** 검증을 통과하면 Claim을, 아니면 빈 값을 돌려준다. 예외를 던지지 않는다. */
    @Override
    public Optional<AccessTokenClaims> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            return Optional.of(new AccessTokenClaims(Long.parseLong(jwt.getSubject()),
                    (String) jwt.getClaims().get(ROLE_CLAIM), (String) jwt.getClaims().get(SESSION_ID_CLAIM),
                    jwt.getExpiresAt()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private OAuth2TokenValidatorResult validateRequiredClaims(Jwt jwt) {
        Object kid = jwt.getHeaders().get("kid");
        Object sid = jwt.getClaims().get(SESSION_ID_CLAIM);
        Object role = jwt.getClaims().get(ROLE_CLAIM);
        List<String> audience = jwt.getAudience();
        Instant expiresAt = jwt.getExpiresAt();
        boolean valid = keyId.equals(kid)
                && audience != null && audience.contains(AUDIENCE)
                && hasRawIssuedAt(jwt.getTokenValue())
                && expiresAt != null && clock.instant().isBefore(expiresAt)
                && sid instanceof String sidValue && SESSION_ID.matcher(sidValue).matches()
                && role instanceof String roleValue && allowedRoles.contains(roleValue)
                && isPositiveLong(jwt.getSubject());
        return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }

    /**
     * 토큰 원문에 {@code iat}가 있는지 본다. Spring Security {@link Jwt} 빌더는 {@code iat}가 없고 {@code exp}가 있으면
     * {@code exp - 1초}를 채우므로 {@link Jwt#getIssuedAt()}으로는 누락을 알 수 없다. 서명 검증은 디코더가 이미 끝냈다.
     */
    private static boolean hasRawIssuedAt(String tokenValue) {
        try {
            return SignedJWT.parse(tokenValue).getJWTClaimsSet().getIssueTime() != null;
        } catch (ParseException ex) {
            return false;
        }
    }

    private static boolean isPositiveLong(String subject) {
        if (subject == null) {
            return false;
        }
        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
