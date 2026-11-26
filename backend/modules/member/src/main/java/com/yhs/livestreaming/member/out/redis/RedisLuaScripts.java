package com.yhs.livestreaming.member.out.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/** {@code classpath:redis/member/*.lua}를 읽어 Redis 스크립트로 만든다. 스크립트는 반환형 Long 또는 List로 쓴다. */
final class RedisLuaScripts {

    private RedisLuaScripts() {
    }

    static <T> DefaultRedisScript<T> load(String name, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/member/" + name + ".lua")));
        script.setResultType(resultType);
        return script;
    }
}
