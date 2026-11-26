package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.member.app.password.PasswordPolicy;
import com.yhs.livestreaming.member.app.password.PasswordViolation;
import com.yhs.livestreaming.member.in.dto.SignupRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidSignupPassword} 검증기. Spring의 검증기 팩토리가 {@link PasswordPolicy}를 생성자로 주입한다.
 * 메시지 템플릿에는 고정 문구만 넣는다(입력값을 넣으면 메시지 표현식으로 해석될 수 있다).
 */
public class SignupPasswordValidator implements ConstraintValidator<ValidSignupPassword, SignupRequest> {

    private final PasswordPolicy passwordPolicy;

    public SignupPasswordValidator(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    @Override
    public boolean isValid(SignupRequest request, ConstraintValidatorContext context) {
        if (request == null || request.password() == null) {
            return true; // 필수 값 검사는 @NotNull이 맡는다
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (!request.password().equals(request.passwordConfirm())) {
            addFieldError(context, "passwordConfirm", "비밀번호 확인이 일치하지 않습니다.");
            valid = false;
        }
        for (PasswordViolation violation : passwordPolicy.check(request.password(), request.username())) {
            addFieldError(context, "password", violation.message());
            valid = false;
        }
        return valid;
    }

    private static void addFieldError(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
    }
}
