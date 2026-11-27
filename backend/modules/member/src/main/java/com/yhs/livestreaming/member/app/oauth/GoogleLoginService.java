package com.yhs.livestreaming.member.app.oauth;

import com.yhs.livestreaming.member.app.otp.LoginChallenge;
import com.yhs.livestreaming.member.app.otp.OtpChallengeStarter;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Google 1차 인증 성공 처리(MB-W11, MB-D023, MB-D025). 로그인을 끝내지 않고 기존 이메일 OTP를 시작할 뿐이다(인증 쿠키·로그인
 * 세션 없음). 순서: claim email_verified 확인 → sub로 GOOGLE 회원 확인·자동 가입(동시 최초 로그인은 충돌 뒤 다시 조회, 만료
 * PENDING은 재생성) → DISABLED 거부 → 이번 Google 이메일로 OTP 시작. DB 쓰기와 OTP(Redis·SMTP)는 하나로 묶을 수 없으므로
 * OTP 단계가 실패해도 만든 PENDING 회원은 남는다(MB-D024).
 */
public class GoogleLoginService {

    private static final int ATTEMPTS = 3;

    private final GoogleAccountRepository accounts;
    private final OtpChallengeStarter otp;
    private final Clock clock;
    private final Supplier<String> nicknameDigits;

    public GoogleLoginService(GoogleAccountRepository accounts, OtpChallengeStarter otp, Clock clock,
                              Supplier<String> nicknameDigits) {
        this.accounts = accounts;
        this.otp = otp;
        this.clock = clock;
        this.nicknameDigits = nicknameDigits;
    }

    public LoginChallenge login(GoogleIdentity identity) {
        String email = identity.email() == null ? "" : identity.email().strip().toLowerCase(Locale.ROOT);
        if (!identity.emailVerified() || email.isEmpty()) {
            throw new OAuthLoginException(OAuthFailure.EMAIL_UNVERIFIED);
        }
        Member member = resolve(identity, email);
        if (member.getAccountStatus() == AccountStatus.DISABLED) {
            throw new OAuthLoginException(OAuthFailure.ACCOUNT_DISABLED);
        }
        return otp.startForLogin(member, email);
    }

    private Member resolve(GoogleIdentity identity, String email) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Instant now = clock.instant();
            Optional<Member> found = accounts.findBySub(identity.sub());
            if (found.isEmpty()) {
                try {
                    return accounts.create(newMember(identity, email), identity.sub(), email);
                } catch (GoogleLinkConflictException conflict) {
                    continue; // 다른 요청이 먼저 만들었다: 새 트랜잭션에서 다시 조회
                }
            }
            Member member = found.get();
            if (!member.isPendingExpired(now)) {
                return member;
            }
            Optional<Member> replaced = accounts.replaceExpiredPending(member.getId(), identity.sub(),
                    now.minus(Member.PENDING_TTL), newMember(identity, email), email);
            if (replaced.isPresent()) {
                return replaced.get();
            }
            // 조건부 삭제가 맞지 않았다(동시 재생성 등): 현재 상태를 다시 확인한다
        }
        throw new IllegalStateException("GOOGLE 회원을 확정하지 못했다");
    }

    private Member newMember(GoogleIdentity identity, String email) {
        return Member.registerGoogle(email, GoogleNickname.of(identity.name(), nicknameDigits));
    }
}
