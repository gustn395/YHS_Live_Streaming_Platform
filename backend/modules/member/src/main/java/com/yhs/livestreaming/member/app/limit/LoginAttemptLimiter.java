package com.yhs.livestreaming.member.app.limit;

import java.time.Duration;
import java.util.Optional;

/**
 * username 기준 로그인 실패 제한 포트(MB-D017). 없는 username에도 같은 규칙을 쓴다. 저장소를 쓸 수 없으면
 * {@code LoginSessionStoreUnavailableException}(503)을 던진다(MB-D019).
 */
public interface LoginAttemptLimiter {

    /** 차단 중이면 남은 시간. 비밀번호를 검증하기 전에 부른다. */
    Optional<Duration> blockedFor(String username);

    /** 실패 1회를 기록한다. 차단 중이면 늘리지도 연장하지도 않는다. 이번 실패로 차단되면 blocked=true. */
    FailureOutcome recordFailure(String username);

    /** 1차 인증 성공: 카운터를 지운다. 그 사이 다른 요청이 차단을 만들었으면 남은 시간을 돌려주고 지우지 않는다. */
    Optional<Duration> recordSuccess(String username);

    record FailureOutcome(boolean blocked, Duration retryAfter) {
    }
}
