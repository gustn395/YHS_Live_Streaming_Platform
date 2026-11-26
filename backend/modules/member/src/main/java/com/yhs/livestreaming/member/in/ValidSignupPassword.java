package com.yhs.livestreaming.member.in;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 회원가입 비밀번호 검증: 비밀번호 확인 일치와 비밀번호 정책(MB-D004, MB-D005). 위반은 {@code password}·{@code passwordConfirm}
 * 필드 오류로 등록돼 오류 응답의 {@code errors[]}에 들어간다.
 */
@Documented
@Constraint(validatedBy = SignupPasswordValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSignupPassword {

    String message() default "비밀번호가 정책에 맞지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
