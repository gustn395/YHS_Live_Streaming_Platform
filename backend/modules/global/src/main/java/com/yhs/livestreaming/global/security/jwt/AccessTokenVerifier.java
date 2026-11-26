package com.yhs.livestreaming.global.security.jwt;

import java.util.Optional;

/** Access Token 검증(MB-D012). 실패 이유는 돌려주지 않고 예외도 던지지 않는다. */
@FunctionalInterface
public interface AccessTokenVerifier {

    Optional<AccessTokenClaims> verify(String token);
}
