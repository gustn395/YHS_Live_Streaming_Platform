package com.yhs.livestreaming.global.error;

import java.time.Duration;
import java.util.Objects;

/**
 * 요청 제한 초과(429). {@link GlobalExceptionHandler}가 {@code Retry-After} 헤더(초, 1 이상)를 붙인다(MB-D017, MB-D018).
 */
public class TooManyRequestsException extends BusinessException {

    private final Duration retryAfter;

    public TooManyRequestsException(ErrorCode errorCode, String detail, Duration retryAfter) {
        super(errorCode, detail);
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
    }

    /** 다시 시도할 수 있을 때까지 남은 시간. 헤더에는 올림한 초(최소 1)로 나간다. */
    public Duration retryAfter() {
        return retryAfter;
    }

    /** 헤더 값: 남은 시간을 초 단위로 올림하고 최소 1초로 둔다. */
    public long retryAfterSeconds() {
        long millis = Math.max(0, retryAfter.toMillis());
        return Math.max(1, (millis + 999) / 1000);
    }
}
