package com.yhs.livestreaming.member.app.oauth;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth 임시 인증 상태 저장 포트(MB-D024). 키는 브라우저 쿠키 난수의 SHA-256(handle)이고 값은 문자열 필드다. 필드
 * {@code state}가 요청의 state와 같을 때만 돌려준다. 저장소에 닿지 못하면
 * {@link com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException}을 던진다.
 */
public interface OAuthStateStore {

    void save(String handle, Map<String, String> fields, Duration ttl);

    /** 소비하지 않고 읽는다. */
    Optional<Map<String, String>> find(String handle, String expectedState);

    /** state가 같을 때만 원자적으로 읽고 지운다(1회). 다르면 아무것도 지우지 않는다(최신 상태 보존). */
    Optional<Map<String, String>> consume(String handle, String expectedState);
}
