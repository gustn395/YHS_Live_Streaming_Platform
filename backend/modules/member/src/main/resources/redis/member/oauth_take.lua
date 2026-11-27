-- OAuth 임시 인증 상태 읽기·1회 소비(MB-D024).
-- KEYS[1] = auth:oauth:{sha256(쿠키 난수)}, ARGV[1] = 요청의 state, ARGV[2] = '1'이면 소비(읽고 삭제), '0'이면 읽기만
-- 반환: 필드·값 평면 목록, 없거나 자료형이 다르거나 state가 다르면 빈 목록. state가 다르면(이전 요청의 늦은 콜백) 지우지 않는다.
if redis.call('TYPE', KEYS[1])['ok'] ~= 'hash' then
    return {}
end
if redis.call('HGET', KEYS[1], 'state') ~= ARGV[1] then
    return {}
end
local fields = redis.call('HGETALL', KEYS[1])
if ARGV[2] == '1' then
    redis.call('DEL', KEYS[1])
end
return fields
