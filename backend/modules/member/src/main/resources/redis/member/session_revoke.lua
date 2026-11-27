-- 로그인 세션 폐기(MB-D022). 소유 회원이 같을 때만 세션과 그 세션의 현재 Refresh 키를 지운다.
-- KEYS[1] = auth:session:{sid}
-- ARGV[1] = memberId, ARGV[2] = sid, ARGV[3] = Refresh 키 접두사(auth:refresh:)
-- 반환: 1 폐기함, 0 세션 없음, -1 소유 회원 다름(아무것도 지우지 않음)
-- 이전(USED) Refresh 키는 지우지 않는다: 세션이 없으면 어떤 Refresh도 쓸 수 없고, 절대 만료 시각에 사라진다.

if redis.call('TYPE', KEYS[1])['ok'] ~= 'hash' then
    return 0
end
local session = redis.call('HMGET', KEYS[1], 'memberId', 'refreshHash')
if session[1] ~= ARGV[1] then
    return -1
end
redis.call('DEL', KEYS[1])
if session[2] then
    local currentKey = ARGV[3] .. session[2]
    if redis.call('TYPE', currentKey)['ok'] == 'hash' and redis.call('HGET', currentKey, 'sid') == ARGV[2] then
        redis.call('DEL', currentKey)
    end
end
return 1
