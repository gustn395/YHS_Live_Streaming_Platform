package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.member.app.otp.LoginChallenge;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStarter;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.TooManyRequestsException;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.limit.LoginAttemptLimiter;
import com.yhs.livestreaming.member.app.limit.LoginAttemptLimiter.FailureOutcome;
import com.yhs.livestreaming.member.app.password.DummyPasswordHash;
import com.yhs.livestreaming.member.app.password.PasswordHasher;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로그인 1차 인증(MB-W06, MB-D011, MB-D017). 성공해도 토큰을 주지 않고 OTP Challenge를 시작한다.
 *
 * <p>순서: 차단 확인(차단 중이면 비밀번호를 검증하지 않음) → 회원 조회 → 비밀번호 비교(없는 username·GOOGLE은 Dummy Hash) →
 * 실패·PENDING 만료면 실패 기록(401 또는 10번째부터 429) → DISABLED면 403(초기화·OTP 없음) → 실패 카운터 초기화(그 사이 차단이
 * 생겼으면 429) → 재해시(필요할 때, 실패해도 계속) → OTP 시작.
 *
 * <p>트랜잭션을 두지 않는다. 해시 계산(수백 ms)과 Redis·메일 호출 동안 DB 연결을 잡지 않는다. 재해시만 짧은 UPDATE 하나다.
 * IP 제한은 입력 계층이 먼저 건다(MB-D018). 비밀번호 원문은 로그·Redis·Challenge에 남기지 않는다.
 */
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final MemberRepository members;
    private final PasswordHasher hasher;
    private final DummyPasswordHash dummyHash;
    private final LoginAttemptLimiter attempts;
    private final OtpChallengeStarter otp;
    private final Clock clock;

    public LoginService(MemberRepository members, PasswordHasher hasher, DummyPasswordHash dummyHash,
                        LoginAttemptLimiter attempts, OtpChallengeStarter otp, Clock clock) {
        this.members = members;
        this.hasher = hasher;
        this.dummyHash = dummyHash;
        this.attempts = attempts;
        this.otp = otp;
        this.clock = clock;
    }

    /**
     * @param username    입력 계층에서 정규화한 username(ASCII만 소문자)
     * @param rawPassword 비밀번호 원문(Trim·정규화하지 않음. 비교 때 NFC는 해셔가 한다)
     */
    public LoginChallenge login(String username, String rawPassword) {
        attempts.blockedFor(username).ifPresent(left -> {
            throw blocked(left);
        });

        Optional<Member> found = members.findByUsername(username)
                .filter(member -> member.getAccountType() == AccountType.LOCAL && member.getPasswordHash() != null);
        boolean matched;
        if (found.isPresent()) {
            matched = hasher.matches(rawPassword, found.get().getPasswordHash());
        } else {
            dummyHash.burn(rawPassword);
            matched = false;
        }

        Instant now = clock.instant();
        if (!matched || found.get().isPendingExpired(now)) {
            FailureOutcome outcome = attempts.recordFailure(username);
            if (outcome.blocked()) {
                throw blocked(outcome.retryAfter());
            }
            throw rejected(MemberErrorCode.LOGIN_FAILED);
        }
        Member member = found.get();
        if (member.getAccountStatus() == AccountStatus.DISABLED) {
            throw rejected(MemberErrorCode.ACCOUNT_DISABLED);
        }
        attempts.recordSuccess(username).ifPresent(left -> {
            throw blocked(left);
        });

        rehashIfNeeded(member, rawPassword, now);
        return otp.startForLogin(member, member.getEmail());
    }

    /** MB-D006 세부: 비밀번호 일치·로그인 가능 상태 확인 뒤, 기존 해시를 조건으로 바꾼다. 실패해도 로그인은 계속한다. */
    private void rehashIfNeeded(Member member, String rawPassword, Instant now) {
        String stored = member.getPasswordHash();
        if (!hasher.needsRehash(stored)) {
            return;
        }
        try {
            String rehashed = hasher.hash(rawPassword);
            members.updatePasswordHash(member.getId(), stored, rehashed, now);
        } catch (RuntimeException ex) {
            // 예외 메시지에는 SQL·해시가 들어 있을 수 있어 종류만 남긴다
            log.warn("비밀번호 재해시 실패(기존 해시 유지, 로그인 계속): memberId={}, cause={}", member.getId(),
                    ex.getClass().getSimpleName());
        }
    }

    private static TooManyRequestsException blocked(Duration retryAfter) {
        MemberErrorCode code = MemberErrorCode.LOGIN_TEMPORARILY_BLOCKED;
        return new TooManyRequestsException(code, code.detail(), retryAfter);
    }

    private static BusinessException rejected(MemberErrorCode code) {
        return new BusinessException(code, code.detail());
    }
}
