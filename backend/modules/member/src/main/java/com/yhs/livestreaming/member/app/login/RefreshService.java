package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.security.jwt.AccessTokenIssuer;
import com.yhs.livestreaming.global.security.jwt.IssuedAccessToken;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.LoginSessionStore.RotateResult;
import com.yhs.livestreaming.member.app.session.RandomTokens;
import com.yhs.livestreaming.member.app.session.RefreshRotation;
import com.yhs.livestreaming.member.app.session.RefreshTokenRecord;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refresh(MB-W09, MB-D020, MB-D021).
 *
 * <p>순서:
 * <ol>
 *     <li>쿠키 값 형식 확인(43자 Base64url이 아니면 저장소를 보지 않고 401).</li>
 *     <li>해시로 세션 조회. 없으면 401. 이미 교체된(USED) 토큰이면 DB를 보기 전에 세션을 폐기하고 401(재사용 탐지).</li>
 *     <li>회원 재조회. DB 장애는 그대로 전파한다(500, 아무것도 바꾸지 않음). 없음·PENDING → 세션 폐기 + 401,
 *     DISABLED → 세션 폐기 + 403.</li>
 *     <li>DB의 role로 Access 서명(메모리) → Rotation(Lua가 관계·만료·현재 해시를 다시 확인). 새 해시 충돌이면 새 난수로 한 번
 *     더, 다시 충돌하면 내부 오류(쿠키 미발급).</li>
 * </ol>
 * 조회 결과는 판단 참고용이고 최종 판단은 Rotation이 한다. 로그에 토큰 원문·해시·sid를 남기지 않는다.
 */
public class RefreshService {

    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);
    private static final String REUSE_LOG = "Refresh Token 재사용을 탐지해 로그인 세션을 폐기했다";

    private final MemberRepository members;
    private final LoginSessionStore sessions;
    private final AccessTokenIssuer accessTokens;
    private final Clock clock;
    private final Supplier<String> newToken;

    public RefreshService(MemberRepository members, LoginSessionStore sessions, AccessTokenIssuer accessTokens,
                          Clock clock, Supplier<String> newToken) {
        this.members = members;
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.clock = clock;
        this.newToken = newToken;
    }

    public CompletedLogin refresh(String rawRefreshToken) {
        if (!RandomTokens.isWellFormed(rawRefreshToken)) {
            throw invalid();
        }
        String currentHash = RandomTokens.sha256Hex(rawRefreshToken);
        RefreshTokenRecord found = sessions.findRefresh(currentHash).orElseThrow(RefreshService::invalid);
        if (!found.current()) {
            sessions.revoke(found.sessionId(), found.memberId());
            log.warn(REUSE_LOG);
            throw invalid();
        }
        if (!found.expiresAt().isAfter(clock.instant())) {
            throw invalid();
        }

        Optional<Member> loaded = members.findById(found.memberId()); // DB 장애는 회원 없음과 다르게 그대로 전파
        if (loaded.isEmpty() || loaded.get().getAccountStatus() == AccountStatus.PENDING) {
            sessions.revoke(found.sessionId(), found.memberId());
            throw invalid();
        }
        Member member = loaded.get();
        if (member.getAccountStatus() == AccountStatus.DISABLED) {
            sessions.revoke(found.sessionId(), found.memberId());
            MemberErrorCode code = MemberErrorCode.ACCOUNT_DISABLED;
            throw new BusinessException(code, code.detail());
        }

        String role = member.getRole().name();
        for (int attempt = 0; attempt < 2; attempt++) {
            String refreshToken = newToken.get();
            IssuedAccessToken accessToken =
                    accessTokens.issue(member.getId(), role, found.sessionId(), found.expiresAt());
            RotateResult result = sessions.rotate(new RefreshRotation(found.sessionId(), found.memberId(),
                    currentHash, RandomTokens.sha256Hex(refreshToken), found.expiresAt()));
            switch (result) {
                case ROTATED:
                    return new CompletedLogin(member.getId(), role, accessToken, refreshToken, found.expiresAt());
                case REUSED:
                    log.warn(REUSE_LOG);
                    throw invalid();
                case INVALID:
                    throw invalid();
                case COLLISION:
                    continue;
                default:
                    throw new IllegalStateException("알 수 없는 Rotation 결과");
            }
        }
        throw new IllegalStateException("Refresh Token 해시 충돌이 반복됐다");
    }

    private static BusinessException invalid() {
        MemberErrorCode code = MemberErrorCode.REFRESH_INVALID;
        return new BusinessException(code, code.detail());
    }
}
