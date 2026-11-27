package com.yhs.livestreaming.member.out.redis;

import com.yhs.livestreaming.global.auth.LoginSessionStatus;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.global.auth.LoginSessionVerifier;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import com.yhs.livestreaming.member.app.session.RefreshRotation;
import com.yhs.livestreaming.member.app.session.RefreshTokenRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 로그인 세션 저장소(MB-D014, MB-D021, MB-D022, G-D007). global {@link LoginSessionVerifier}의 구현이기도 하다.
 *
 * <ul>
 *     <li>{@code auth:session:{sid}} HASH: memberId, refreshHash(현재 Refresh 해시), expiresAt(epoch ms). 절대 만료
 *     시각에 사라진다.</li>
 *     <li>{@code auth:refresh:{sha256}} HASH: sid, memberId, expiresAt. 같은 절대 만료 시각. 교체된 이전 해시 키도 만료까지
 *     남는다: 세션의 현재 해시와 다르면 사용된(USED) 토큰이다(별도 상태 값 없음).</li>
 *     <li>저장은 {@code session_create.lua}(두 키가 모두 없을 때만), 교체는 {@code refresh_rotate.lua}, 폐기는
 *     {@code session_revoke.lua}로 한다. Lua 안에서 키를 만들 때(현재 Refresh 키) 단일 Redis를 전제로 한다(G-D007).</li>
 * </ul>
 * Redis 오류는 {@link LoginSessionStoreUnavailableException}으로 바꾼다(503, MB-D019). Session ID·해시는 로그에 남기지 않는다.
 */
@Component
public class RedisLoginSessionStore implements LoginSessionStore, LoginSessionVerifier {

    static final String SESSION_PREFIX = "auth:session:";
    static final String REFRESH_PREFIX = "auth:refresh:";
    private static final DefaultRedisScript<Long> CREATE = RedisLuaScripts.load("session_create", Long.class);
    private static final DefaultRedisScript<Long> ROTATE = RedisLuaScripts.load("refresh_rotate", Long.class);
    private static final DefaultRedisScript<Long> REVOKE = RedisLuaScripts.load("session_revoke", Long.class);

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
    public Optional<RefreshTokenRecord> findRefresh(String refreshTokenHash) {
        List<Object> refresh;
        List<Object> session;
        try {
            refresh = redis.opsForHash().multiGet(REFRESH_PREFIX + refreshTokenHash, List.of("sid", "memberId"));
            if (refresh.get(0) == null || refresh.get(1) == null) {
                return Optional.empty();
            }
            session = redis.opsForHash().multiGet(SESSION_PREFIX + refresh.get(0),
                    List.of("memberId", "refreshHash", "expiresAt"));
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        // 세션이 없거나 다른 회원 것이면 이 Refresh로 찾을 세션이 없다(관계 확인)
        if (session.get(0) == null || !session.get(0).equals(refresh.get(1)) || session.get(2) == null) {
            return Optional.empty();
        }
        long memberId;
        Instant expiresAt;
        try {
            memberId = Long.parseLong((String) refresh.get(1));
            expiresAt = Instant.ofEpochMilli(Long.parseLong((String) session.get(2)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        boolean current = refreshTokenHash.equals(session.get(1));
        return Optional.of(new RefreshTokenRecord((String) refresh.get(0), memberId, expiresAt, current));
    }

    @Override
    public RotateResult rotate(RefreshRotation rotation) {
        Long result;
        try {
            result = redis.execute(ROTATE,
                    List.of(SESSION_PREFIX + rotation.sessionId(), REFRESH_PREFIX + rotation.currentHash(),
                            REFRESH_PREFIX + rotation.newHash()),
                    rotation.sessionId(), Long.toString(rotation.memberId()), rotation.currentHash(),
                    rotation.newHash(), Long.toString(clock.millis()),
                    Long.toString(rotation.expiresAt().toEpochMilli()), REFRESH_PREFIX);
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        if (result == null) {
            // 결과를 확인하지 못하면 교체됐다고 보지 않는다(새 쿠키 미발급)
            throw new LoginSessionStoreUnavailableException("Refresh 교체 결과를 확인할 수 없다", null);
        }
        // 코드는 refresh_rotate.lua 머리말과 같다. long 그대로 비교한다(intValue()는 하위 32비트만 남겨 다른 값을 1로
        // 만들 수 있다). 모르는 값은 무효로 넘기지 않고 확인 불가로 본다(쿠키 미발급).
        long code = result;
        if (code == 1L) {
            return RotateResult.ROTATED;
        }
        if (code == 0L) {
            return RotateResult.INVALID;
        }
        if (code == 2L) {
            return RotateResult.REUSED;
        }
        if (code == 3L) {
            return RotateResult.COLLISION;
        }
        throw new LoginSessionStoreUnavailableException("Refresh 교체 결과를 해석할 수 없다", null);
    }

    @Override
    public RevokeResult revoke(String sessionId, long memberId) {
        Long result;
        try {
            result = redis.execute(REVOKE, List.of(SESSION_PREFIX + sessionId), Long.toString(memberId), sessionId,
                    REFRESH_PREFIX);
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        if (result == null) {
            throw new LoginSessionStoreUnavailableException("세션 폐기 결과를 확인할 수 없다", null);
        }
        // 코드는 session_revoke.lua 머리말과 같다. long 그대로 비교하고, 모르는 값은 폐기 실패로 본다(로그아웃 503, 쿠키 유지).
        long code = result;
        if (code == 1L) {
            return RevokeResult.REVOKED;
        }
        if (code == 0L) {
            return RevokeResult.NOT_FOUND;
        }
        if (code == -1L) {
            return RevokeResult.MEMBER_MISMATCH;
        }
        throw new LoginSessionStoreUnavailableException("세션 폐기 결과를 해석할 수 없다", null);
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
