-- OAuth 임시 인증 상태 저장(MB-D024).
-- KEYS[1] = auth:oauth:{sha256(쿠키 난수)}, ARGV[1] = TTL(ms), ARGV[2..] = 필드·값 쌍
-- 같은 키가 있으면 덮어쓴다(256비트 난수라 사실상 없음). 저장과 만료 설정을 한 번에 한다.
redis.call('DEL', KEYS[1])
local kv = {}
for i = 2, #ARGV do
    kv[#kv + 1] = ARGV[i]
end
redis.call('HSET', KEYS[1], unpack(kv))
redis.call('PEXPIRE', KEYS[1], ARGV[1])
return 1
