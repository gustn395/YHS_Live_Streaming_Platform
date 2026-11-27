package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.security.jwt.AccessTokenIssuer;
import com.yhs.livestreaming.global.security.jwt.IssuedAccessToken;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.LoginSessionStore.CreateResult;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import com.yhs.livestreaming.member.app.otp.VerifiedOtp;
import com.yhs.livestreaming.member.app.session.RandomTokens;
import com.yhs.livestreaming.member.app.oauth.GoogleAccountRepository;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * OTP 최종 인증 뒤 로그인 완료(MB-W08, MB-D003, MB-D011~D014).
 *
 * <p>순서와 중간 실패:
 * <ol>
 *     <li>회원 재조회·상태 재확인: 없음·PENDING 만료 → 401, DISABLED → 403(토큰 없음).</li>
 *     <li>PENDING이면 조건부 활성화 UPDATE(PENDING이고 만료 전일 때만). 0행이면 다시 조회해 ACTIVE일 때만 계속한다.</li>
 *     <li>Session ID·Refresh Token 생성 → Access Token 서명(메모리) → 세션·Refresh 해시 원자 저장. 키 충돌이면 새 값으로
 *     한 번 더, 저장 실패·결과 미확인이면 예외(인증 쿠키 미발급).</li>
 * </ol>
 * Challenge는 이 단계 전에 이미 소비됐다. 여기서 실패하면 회원은 다시 로그인해야 한다(활성화는 OTP로 이메일 소유가 확인된 뒤라
 * 남아도 정당하다).
 */
public class LoginCompletionService {

    static final Duration SESSION_TTL = Duration.ofDays(7);

    private final MemberRepository members;
    private final LoginSessionStore sessions;
    private final AccessTokenIssuer accessTokens;
    private final Clock clock;
    private final GoogleAccountRepository googleAccounts;

    public LoginCompletionService(MemberRepository members, LoginSessionStore sessions,
                                  AccessTokenIssuer accessTokens, Clock clock, GoogleAccountRepository googleAccounts) {
        this.members = members;
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.clock = clock;
        this.googleAccounts = googleAccounts;
    }

    public CompletedLogin complete(VerifiedOtp verified) {
        Instant now = clock.instant();
        long memberId = verified.memberId();
        Member member = loginAllowed(members.findById(memberId), now);
        if (member.getAccountType() == AccountType.GOOGLE) {
            member = confirmGoogle(member, verified.deliveryEmail(), now);
        } else if (member.getAccountStatus() == AccountStatus.PENDING) {
            int rows = members.activatePending(memberId, now.minus(Member.PENDING_TTL), now);
            if (rows != 1) {
                Member reloaded = loginAllowed(members.findById(memberId), now);
                if (reloaded.getAccountStatus() != AccountStatus.ACTIVE) {
                    throw rejected(MemberErrorCode.LOGIN_FAILED);
                }
                member = reloaded;
            }
        }
        return issueSession(member.getId(), member.getRole().name(), now);
    }

    /**
     * GOOGLE(MB-D025): OTP를 받은 주소(Challenge의 수신 주소)가 이제 서비스가 확인한 이메일이다. PENDING은 활성화와 함께,
     * ACTIVE는 바뀐 경우에만 회원·연결 행 이메일을 같은 트랜잭션으로 갱신한다. 조건부 갱신이 0행이면 상태를 다시 확인한다.
     * 갱신 뒤 로그인 세션 저장이 실패하면 LOCAL 활성화와 같이 DB 변경은 남고 인증 쿠키는 발급하지 않는다(다시 로그인).
     */
    private Member confirmGoogle(Member member, String deliveryEmail, Instant now) {
        long memberId = member.getId();
        if (deliveryEmail == null || deliveryEmail.isBlank()) {
            throw rejected(MemberErrorCode.LOGIN_FAILED);
        }
        if (member.getAccountStatus() == AccountStatus.PENDING) {
            if (googleAccounts.activatePending(memberId, deliveryEmail, now.minus(Member.PENDING_TTL), now) == 1) {
                return active(loginAllowed(members.findById(memberId), now));
            }
            member = active(loginAllowed(members.findById(memberId), now));
        }
        if (!deliveryEmail.equals(member.getEmail())
                && googleAccounts.updateActiveEmail(memberId, deliveryEmail, now) != 1) {
            active(loginAllowed(members.findById(memberId), now));
            throw rejected(MemberErrorCode.LOGIN_FAILED);
        }
        return member;
    }

    private static Member active(Member member) {
        if (member.getAccountStatus() != AccountStatus.ACTIVE) {
            throw rejected(MemberErrorCode.LOGIN_FAILED);
        }
        return member;
    }

    private CompletedLogin issueSession(long memberId, String role, Instant now) {
        Instant expiresAt = now.plus(SESSION_TTL);
        for (int attempt = 0; attempt < 2; attempt++) {
            String sessionId = RandomTokens.newToken();
            String refreshToken = RandomTokens.newToken();
            IssuedAccessToken accessToken = accessTokens.issue(memberId, role, sessionId, expiresAt);
            CreateResult result = sessions.create(
                    new NewLoginSession(sessionId, memberId, RandomTokens.sha256Hex(refreshToken), expiresAt));
            if (result == CreateResult.CREATED) {
                return new CompletedLogin(memberId, role, accessToken, refreshToken, expiresAt);
            }
        }
        throw new LoginSessionStoreUnavailableException("로그인 세션 키 충돌이 반복됐다", null);
    }

    private static Member loginAllowed(java.util.Optional<Member> found, Instant now) {
        Member member = found.orElseThrow(() -> rejected(MemberErrorCode.LOGIN_FAILED));
        if (member.getAccountStatus() == AccountStatus.DISABLED) {
            throw rejected(MemberErrorCode.ACCOUNT_DISABLED);
        }
        if (member.isPendingExpired(now)) {
            throw rejected(MemberErrorCode.LOGIN_FAILED);
        }
        return member;
    }

    private static BusinessException rejected(MemberErrorCode code) {
        return new BusinessException(code, code.detail());
    }
}
