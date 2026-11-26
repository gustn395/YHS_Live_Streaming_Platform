-- 로그인 실패 1회 기록(MB-D017).
-- KEYS[1] = auth:login:fail:{sha256(username)}, KEYS[2] = auth:login:block:{sha256(username)}
-- ARGV[1] = 차단까지 연속 실패 수(10), ARGV[2] = 카운터 TTL(ms, 마지막 실패 기준 24시간), ARGV[3] = 차단 시간(ms, 15분)
-- 반환: {1, 남은 차단 ms} 차단 / {0, 현재 실패 수}
-- 차단 중이면 세지도 연장하지도 않는다. 10번째 실패에서 차단 키를 만들고 카운터를 지운다.
local blocked = redis.call('PTTL', KEYS[2])
if blocked > 0 then
    return {1, blocked}
end
local count = redis.call('INCR', KEYS[1])
redis.call('PEXPIRE', KEYS[1], ARGV[2])
if count >= tonumber(ARGV[1]) then
    redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
    redis.call('DEL', KEYS[1])
    return {1, tonumber(ARGV[3])}
end
return {0, count}
