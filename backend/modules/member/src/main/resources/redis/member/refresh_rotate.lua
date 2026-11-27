-- Refresh Token 교체(Rotation)·재사용 탐지(MB-D021).
-- KEYS[1] = auth:session:{sid}, KEYS[2] = auth:refresh:{제출 해시}, KEYS[3] = auth:refresh:{새 해시}
-- ARGV[1] = sid, ARGV[2] = memberId, ARGV[3] = 제출 해시, ARGV[4] = 새 해시, ARGV[5] = 지금(epoch ms),
-- ARGV[6] = 조회 때 확인한 절대 만료 시각(epoch ms), ARGV[7] = Refresh 키 접두사(auth:refresh:)
-- 반환: 1 교체함, 0 무효(아무것도 쓰지 않음), 2 재사용 탐지(세션 폐기), 3 새 해시 충돌(아무것도 쓰지 않음)
--
-- Lua는 원자적이지만 Rollback은 없다. 그래서 확인할 수 있는 조건(자료형, 필드, 관계, 만료, 현재 해시, 충돌)을 모두
-- 첫 쓰기 전에 확인한다. 쓰기 단계는 자료형이 확인된 HASH나 없는 키에만 쓴다. 쓰기 순서는 새 Refresh 키(만료 포함)를
-- 먼저 만들고 세션의 현재 해시를 마지막에 바꾼다: 중간에 멈춰도 세션은 이전 해시를 가리키므로 새 토큰이 쓰이지 않는다.
-- 세션 키가 없으면 절대 만들지 않는다(로그아웃 뒤 지연된 Refresh가 세션을 되살리지 못한다).

if redis.call('TYPE', KEYS[2])['ok'] ~= 'hash' then
    return 0
end
local refresh = redis.call('HMGET', KEYS[2], 'sid', 'memberId')
if refresh[1] ~= ARGV[1] or refresh[2] ~= ARGV[2] then
    return 0
end
if redis.call('TYPE', KEYS[1])['ok'] ~= 'hash' then
    return 0
end
local session = redis.call('HMGET', KEYS[1], 'memberId', 'refreshHash', 'expiresAt')
if session[1] ~= ARGV[2] or not session[2] or session[3] ~= ARGV[6] then
    return 0
end
if tonumber(session[3]) == nil or tonumber(session[3]) <= tonumber(ARGV[5]) then
    return 0
end

if session[2] ~= ARGV[3] then
    -- 이미 교체된(USED) 토큰: 세션과, 세션의 현재 Refresh 키(같은 세션 것일 때만)를 지운다
    local currentKey = ARGV[7] .. session[2]
    redis.call('DEL', KEYS[1])
    if redis.call('TYPE', currentKey)['ok'] == 'hash' and redis.call('HGET', currentKey, 'sid') == ARGV[1] then
        redis.call('DEL', currentKey)
    end
    return 2
end

if redis.call('EXISTS', KEYS[3]) == 1 then
    return 3
end

redis.call('HSET', KEYS[3], 'sid', ARGV[1], 'memberId', ARGV[2], 'expiresAt', session[3])
redis.call('PEXPIREAT', KEYS[3], session[3])
redis.call('HSET', KEYS[1], 'refreshHash', ARGV[4])
return 1
