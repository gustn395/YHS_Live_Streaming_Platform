package com.yhs.livestreaming.member.app.password;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 없는 username·GOOGLE 계정 로그인에도 실제와 같은 비용의 비밀번호 비교를 한 번 해서, 응답 시간 차이로 계정 존재가 드러나지 않게
 * 한다(MB-D011). 기동 때 무작위 값으로 만든 해시를 쓰므로 어떤 입력도 일치하지 않는다.
 */
public class DummyPasswordHash {

    private final PasswordHasher hasher;
    private final String hash;

    public DummyPasswordHash(PasswordHasher hasher, String hash) {
        this.hasher = hasher;
        this.hash = hash;
    }

    /** 현재 해시 설정(반복 횟수·키)으로 무작위 비밀번호의 해시를 만든다. */
    public static DummyPasswordHash create(PasswordHasher hasher) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return new DummyPasswordHash(hasher, hasher.hash(Base64.getEncoder().encodeToString(random)));
    }

    /** 비교 비용만 치른다. 결과는 쓰지 않는다. */
    public void burn(String rawPassword) {
        hasher.matches(rawPassword, hash);
    }
}
