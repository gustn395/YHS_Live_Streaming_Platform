package com.yhs.livestreaming.member.out.redis;

import com.yhs.livestreaming.global.auth.LoginSessionStatus;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.auth.LoginSessionVerifier;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 로그인 세션 저장소(MB-D014, G-D007). global {@link LoginSessionVerifier}의 구현이기도 하다.
 *
 * <ul>
 *     <li>{@code auth:session:{sid}} HASH: memberId, refreshHash, expiresAt(epoch ms). 절대 만료 시각에 사라진다.</li>
 *     <li>{@code auth:refresh:{sha256}} HASH: sid, memberId, expiresAt. 같은 절대 만료 시각.</li>
 *     <li>저장은 {@code session_create.lua} 하나로 한다: 두 키가 모두 없을 때만 쓴다.</li>
 * </ul>
 * Redis 오류는 {@link LoginSessionStoreUnavailableException}으로 바꾼다(503, MB-D019). Session ID·해시는 로그에 남기지 않는다.
 */
@Component
public class RedisLoginSessionStore implements LoginSessionStore, LoginSessionVerifier {

    static final String SESSION_PREFIX = "auth:session:";
    static final String REFRESH_PREFIX = "auth:refresh:";
    private static final DefaultRedisScript<Long> CREATE = RedisLuaScripts.load("session_create", Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisLoginSessionStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public CreateResult create(NewLoginSession session) {
        if (!session.expiresAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("이미 만료된 로그인 세션은 저장하지 않는다");
        }
        String expiresAt = Long.toString(session.expiresAt().toEpochMilli());
        Long result;
        try {
            result = redis.execute(CREATE,
                    List.of(SESSION_PREFIX + session.sessionId(), REFRESH_PREFIX + session.refreshTokenHash()),
                    Long.toString(session.memberId()), session.refreshTokenHash(), session.sessionId(), expiresAt);
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        if (result == null) {
            // 결과를 확인하지 못하면 저장됐다고 보지 않는다(인증 쿠키 미발급).
            throw new LoginSessionStoreUnavailableException("로그인 세션 저장 결과를 확인할 수 없다", null);
        }
        return result == 1L ? CreateResult.CREATED : CreateResult.COLLISION;
    }

    @Override
    public LoginSessionStatus verify(String sessionId, long memberId) {
        Object owner;
        try {
            owner = redis.opsForHash().get(SESSION_PREFIX + sessionId, "memberId");
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        if (owner == null) {
            return LoginSessionStatus.NOT_FOUND;
        }
        return Long.toString(memberId).equals(owner) ? LoginSessionStatus.ACTIVE : LoginSessionStatus.MEMBER_MISMATCH;
    }

    private static LoginSessionStoreUnavailableException unavailable(DataAccessException ex) {
        return new LoginSessionStoreUnavailableException("로그인 세션 저장소를 사용할 수 없다", ex);
    }
}
