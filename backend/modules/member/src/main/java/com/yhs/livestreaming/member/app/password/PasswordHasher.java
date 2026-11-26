package com.yhs.livestreaming.member.app.password;

/** 비밀번호 해시 계약(MB-D006). 구현은 {@link Pbkdf2PasswordHasher}다. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String storedHash);

    boolean needsRehash(String storedHash);
}
