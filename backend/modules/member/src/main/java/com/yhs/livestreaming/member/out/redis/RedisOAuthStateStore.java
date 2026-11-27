package com.yhs.livestreaming.member.out.redis;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import com.yhs.livestreaming.member.app.oauth.OAuthStateStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis OAuth 임시 인증 상태(MB-D024): {@code auth:oauth:{handle}} HASH, TTL은 저장 때 정한다. 조회·소비는 state 비교와 함께
 * Lua 하나로 한다. 값(state·nonce·PKCE verifier)은 로그에 남기지 않는다.
 */
@Component
public class RedisOAuthStateStore implements OAuthStateStore {

    static final String PREFIX = "auth:oauth:";
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> TAKE = RedisLuaScripts.load("oauth_take", List.class);
    private static final DefaultRedisScript<Long> SAVE = RedisLuaScripts.load("oauth_save", Long.class);

    private final StringRedisTemplate redis;

    public RedisOAuthStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String handle, Map<String, String> fields, Duration ttl) {
        List<String> args = new ArrayList<>();
        args.add(Long.toString(ttl.toMillis()));
        fields.forEach((key, value) -> {
            args.add(key);
            args.add(value);
        });
        try {
            redis.execute(SAVE, List.of(PREFIX + handle), args.toArray());
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public Optional<Map<String, String>> find(String handle, String expectedState) {
        return take(handle, expectedState, false);
    }

    @Override
    public Optional<Map<String, String>> consume(String handle, String expectedState) {
        return take(handle, expectedState, true);
    }

    private Optional<Map<String, String>> take(String handle, String expectedState, boolean consume) {
        if (expectedState == null || expectedState.isEmpty()) {
            return Optional.empty();
        }
        List<?> flat;
        try {
            flat = redis.execute(TAKE, List.of(PREFIX + handle), expectedState, consume ? "1" : "0");
        } catch (DataAccessException ex) {
            throw unavailable(ex);
        }
        if (flat == null || flat.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            fields.put(String.valueOf(flat.get(i)), String.valueOf(flat.get(i + 1)));
        }
        return Optional.of(fields);
    }

    private static LoginSessionStoreUnavailableException unavailable(DataAccessException ex) {
        return new LoginSessionStoreUnavailableException("OAuth 임시 상태 저장소를 사용할 수 없다", ex);
    }
}
