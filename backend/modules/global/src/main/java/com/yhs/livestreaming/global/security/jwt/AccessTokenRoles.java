package com.yhs.livestreaming.global.security.jwt;

import java.util.Set;

/**
 * Access Token의 {@code role} Claim으로 허용할 값(MB-D012). global은 회원 역할 enum을 모르므로(의존 방향) 회원 모듈이
 * 이 Bean으로 넘긴다.
 */
@FunctionalInterface
public interface AccessTokenRoles {

    Set<String> allowedRoles();
}
