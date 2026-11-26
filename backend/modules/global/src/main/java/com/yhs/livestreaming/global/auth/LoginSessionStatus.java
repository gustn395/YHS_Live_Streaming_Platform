package com.yhs.livestreaming.global.auth;

/** {@link LoginSessionVerifier} 검증 결과. ACTIVE만 통과다. */
public enum LoginSessionStatus {
    ACTIVE,
    NOT_FOUND,
    MEMBER_MISMATCH
}
