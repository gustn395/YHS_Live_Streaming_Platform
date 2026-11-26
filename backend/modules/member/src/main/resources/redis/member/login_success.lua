-- 1차 인증 성공 처리(MB-D017).
-- KEYS[1] = auth:login:fail:{sha256(username)}, KEYS[2] = auth:login:block:{sha256(username)}
-- 반환: 0 초기화함 / 남은 차단 ms(비밀번호를 검증하는 사이 다른 요청이 차단을 만든 경우, 초기화하지 않음)
local blocked = redis.call('PTTL', KEYS[2])
if blocked > 0 then
    return blocked
end
redis.call('DEL', KEYS[1])
return 0
