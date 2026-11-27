package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.RandomTokens;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 로그아웃(MB-W10, MB-D022). 이 브라우저가 쥔 로그인 세션을 모두 폐기한다.
 * <ul>
 *     <li>Access: 보안 필터가 JWT 전체 검증과 Redis 세션 소유자 확인을 통과시킨 인증 주체만 받는다(없으면 null).</li>
 *     <li>Refresh: 해시로 찾은 세션(이미 교체된 USED 토큰 포함, 세션·회원 관계 확인).</li>
 * </ul>
 * 찾은 sid마다 소유 회원 확인 뒤 폐기한다. 폐기할 것이 없어도 성공이다(멱등). 저장소 오류는 그대로 전파한다(503): 일부만 폐기된
 * 뒤 실패해도 다시 호출하면 이미 지운 쪽은 아무 일도 하지 않고 나머지를 지운다.
 */
public class LogoutService {

    private final LoginSessionStore sessions;

    public LogoutService(LoginSessionStore sessions) {
        this.sessions = sessions;
    }

    public void logout(AuthenticatedMember accessPrincipal, String rawRefreshToken) {
        Map<String, Long> targets = new LinkedHashMap<>();
        if (accessPrincipal != null) {
            targets.put(accessPrincipal.sessionId(), accessPrincipal.memberId());
        }
        if (RandomTokens.isWellFormed(rawRefreshToken)) {
            sessions.findRefresh(RandomTokens.sha256Hex(rawRefreshToken))
                    .ifPresent(found -> targets.putIfAbsent(found.sessionId(), found.memberId()));
        }
        targets.forEach(sessions::revoke);
    }
}
