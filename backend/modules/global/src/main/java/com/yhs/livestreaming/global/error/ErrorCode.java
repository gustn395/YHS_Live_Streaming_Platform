package com.yhs.livestreaming.global.error;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드 계약(G-D003). 각 모듈은 자기 오류 코드를 이 인터페이스의 enum으로 정의하고 {@link BusinessException}에 담는다.
 * {@link #code()}는 ProblemDetail의 확장 필드 {@code code}로 나가는 기계용 값이다(예: {@code MEMBER_USERNAME_DUPLICATE}).
 */
public interface ErrorCode {

    HttpStatus status();

    String code();
}
