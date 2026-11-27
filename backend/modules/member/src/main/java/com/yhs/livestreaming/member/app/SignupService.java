package com.yhs.livestreaming.member.app;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.member.app.password.BreachedPasswordChecker;
import com.yhs.livestreaming.member.app.password.PasswordHasher;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 일반 회원가입(MB-W04, 명세 M-01). 가입 결과는 PENDING 회원이고 토큰은 발급하지 않는다(MB-D003).
 *
 * <p>순서: 사전 중복 검사 → 유출 비밀번호 검사(HIBP, MB-D010) → 해시 → 저장. 유출이면 400, 확인할 수 없으면 503으로
 * 가입을 거부하고(fail-closed) 해시·저장을 하지 않는다.
 *
 * <p>트랜잭션: HIBP 호출(최대 수 초)과 비밀번호 해시(PBKDF2 600,000회, 수백 ms)를 DB 연결을 잡기 전에 하려고 이 메서드에는
 * 트랜잭션을 두지 않는다.
 * 저장은 {@link MemberRepository#saveReclaimingExpired}의 트랜잭션 하나다: 만료 LOCAL PENDING이 쥔 username·이메일을
 * 회수(조건부 삭제)하고 새 회원을 넣는다(MB-D030). 사전 중복 검사는 사용자에게 빠르게 알려 주는 용도이고 만료 PENDING은 세지
 * 않으며, 동시 가입은 DB UNIQUE 제약이 막는다(MB-D008).
 */
@Service
public class SignupService {

    private final MemberRepository members;
    private final PasswordHasher passwordHasher;
    private final BreachedPasswordChecker breachedPasswordChecker;
    private final Clock clock;

    SignupService(MemberRepository members, PasswordHasher passwordHasher, Clock clock,
            BreachedPasswordChecker breachedPasswordChecker) {
        this.clock = clock;
        this.members = members;
        this.passwordHasher = passwordHasher;
        this.breachedPasswordChecker = breachedPasswordChecker;
    }

    public SignupResult signup(SignupCommand command) {
        Instant expiredAtOrBefore = clock.instant().minus(Member.PENDING_TTL);
        if (members.isUsernameTaken(command.username(), expiredAtOrBefore)) {
            throw rejected(MemberErrorCode.USERNAME_DUPLICATE);
        }
        if (members.isLocalEmailTaken(command.email(), expiredAtOrBefore)) {
            throw rejected(MemberErrorCode.EMAIL_DUPLICATE);
        }
        switch (breachedPasswordChecker.check(command.rawPassword())) {
            case BREACHED -> throw rejected(MemberErrorCode.PASSWORD_BREACHED);
            case UNAVAILABLE -> throw rejected(MemberErrorCode.PASSWORD_CHECK_UNAVAILABLE);
            case NOT_BREACHED -> {
            }
        }
        String passwordHash = passwordHasher.hash(command.rawPassword());
        // 만료 PENDING이 쥔 username·이메일은 이 저장 트랜잭션에서 회수한다(MB-D030). 기준 시각은 사전 검사와 같다
        Member saved = members.saveReclaimingExpired(
                Member.registerLocal(command.username(), passwordHash, command.email(), command.nickname()),
                expiredAtOrBefore);
        return new SignupResult(saved.getId(), saved.getAccountStatus());
    }

    private static BusinessException rejected(MemberErrorCode errorCode) {
        return new BusinessException(errorCode, errorCode.detail());
    }
}
