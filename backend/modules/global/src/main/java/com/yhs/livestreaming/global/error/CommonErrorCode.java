package com.yhs.livestreaming.global.error;

import org.springframework.http.HttpStatus;

/** global이 직접 쓰는 공통 오류 코드(G-D003). 프레임워크 오류(404·405 등)는 {@code COMMON_<상태 이름>}을 쓴다. */
public enum CommonErrorCode implements ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_INVALID_INPUT"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_ACCESS_DENIED"),
    SESSION_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SESSION_STORE_UNAVAILABLE");

    private final HttpStatus status;
    private final String code;

    CommonErrorCode(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }
}
