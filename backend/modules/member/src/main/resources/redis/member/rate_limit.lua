-- IP 기준 고정 창 요청 제한(MB-D018).
-- KEYS[1] = ratelimit:{scope}:{sha256(ip)}
-- ARGV[1] = 창 안 최대 허용 수, ARGV[2] = 창 길이(ms)
-- 반환: {1, 횟수} 허용 / {0, 남은 ms} 초과
-- 증가와 만료 설정을 한 번에 한다. 창은 첫 요청부터 시작하고 이후 요청이 연장하지 않는다.
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
local ttl = redis.call('PTTL', KEYS[1])
if ttl < 0 then
    -- 만료 설정이 빠진 키(이전 장애 등)를 바로잡는다
    redis.call('PEXPIRE', KEYS[1], ARGV[2])
    ttl = tonumber(ARGV[2])
end
if count > tonumber(ARGV[1]) then
    return {0, ttl}
end
return {1, count}
