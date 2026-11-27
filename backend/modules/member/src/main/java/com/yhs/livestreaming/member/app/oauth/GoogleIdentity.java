package com.yhs.livestreaming.member.app.oauth;

/**
 * 검증을 마친 Google ID Token에서 쓰는 값만 모은 것(MB-D023). Google 토큰 자체는 담지 않는다.
 *
 * @param sub           OIDC sub(회원을 찾는 기준)
 * @param email         Google 이메일(정규화 전)
 * @param emailVerified Google claim {@code email_verified}. 서비스 자체 이메일 확인(OTP)과 다르다
 * @param name          Google 이름(닉네임 후보)
 */
public record GoogleIdentity(String sub, String email, boolean emailVerified, String name) {

    @Override
    public String toString() {
        return "GoogleIdentity[sub=****, email=****, emailVerified=" + emailVerified + ", name=****]";
    }
}
