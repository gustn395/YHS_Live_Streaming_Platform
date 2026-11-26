package com.yhs.livestreaming.global.error;

/**
 * 업무 규칙 위반. {@link ErrorCode}의 상태·코드와 사용자에게 보여 줄 detail로 ProblemDetail을 만든다(G-D003).
 * detail에는 비밀번호·토큰·내부 식별 정보 같은 민감 정보를 넣지 않는다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
