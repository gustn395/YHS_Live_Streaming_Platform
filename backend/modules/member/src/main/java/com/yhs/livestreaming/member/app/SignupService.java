package com.yhs.livestreaming.member.app;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.member.app.password.BreachedPasswordChecker;
import com.yhs.livestreaming.member.app.password.PasswordHasher;
import com.yhs.livestreaming.member.domain.Member;
import org.springframework.stereotype.Service;

/**
 * 일반 회원가입(MB-W04, 명세 M-01). 가입 결과는 PENDING 회원이고 토큰은 발급하지 않는다(MB-D003).
 *
 * <p>순서: 사전 중복 검사 → 유출 비밀번호 검사(HIBP, MB-D010) → 해시 → 저장. 유출이면 400, 확인할 수 없으면 503으로
 * 가입을 거부하고(fail-closed) 해시·저장을 하지 않는다.
 *
 * <p>트랜잭션: HIBP 호출(최대 수 초)과 비밀번호 해시(PBKDF2 600,000회, 수백 ms)를 DB 연결을 잡기 전에 하려고 이 메서드에는
 * 트랜잭션을 두지 않는다.
 * 저장은 {@link MemberRepository#save}의 트랜잭션 하나다. 사전 중복 검사는 사용자에게 빠르게 알려 주는 용도이고,
 * 동시 가입은 DB UNIQUE 제약이 막는다(MB-D008).
 */
@Service
public class SignupService {

    private final MemberRepository members;
    private final PasswordHasher passwordHasher;
    private final BreachedPasswordChecker breachedPasswordChecker;

    SignupService(MemberRepository members, PasswordHasher passwordHasher,
            BreachedPasswordChecker breachedPasswordChecker) {
        this.members = members;
        this.passwordHasher = passwordHasher;
        this.breachedPasswordChecker = breachedPasswordChecker;
    }

    public SignupResult signup(SignupCommand command) {
        if (members.existsByUsername(command.username())) {
            throw rejected(MemberErrorCode.USERNAME_DUPLICATE);
        }
        if (members.existsLocalEmail(command.email())) {
            throw rejected(MemberErrorCode.EMAIL_DUPLICATE);
        }
        switch (breachedPasswordChecker.check(command.rawPassword())) {
            case BREACHED -> throw rejected(MemberErrorCode.PASSWORD_BREACHED);
            case UNAVAILABLE -> throw rejected(MemberErrorCode.PASSWORD_CHECK_UNAVAILABLE);
            case NOT_BREACHED -> {
            }
        }
        String passwordHash = passwordHasher.hash(command.rawPassword());
        Member saved = members.save(
                Member.registerLocal(command.username(), passwordHash, command.email(), command.nickname()));
        return new SignupResult(saved.getId(), saved.getAccountStatus());
    }

    private static BusinessException rejected(MemberErrorCode errorCode) {
        return new BusinessException(errorCode, errorCode.detail());
    }
}
