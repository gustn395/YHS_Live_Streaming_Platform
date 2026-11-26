-- 새 로그인의 OTP Challenge 발급(MB-D015, MB-D017).
-- KEYS[1] = auth:otp:member:{id}:current      (현재 challengeId)
-- KEYS[2] = auth:otp:member:{id}:last-issued  (60초 발급 간격)
-- KEYS[3] = auth:otp:member:{id}:issue:1h, KEYS[4] = auth:otp:member:{id}:issue:24h
-- KEYS[5] = auth:otp:member:{id}:fail:24h
-- KEYS[6] = auth:otp:challenge:{새 challengeId}
-- ARGV[1] = challengeId, ARGV[2] = memberId, ARGV[3] = otpHmac, ARGV[4] = keyId
-- ARGV[5] = otpExpiresAt(ms), ARGV[6] = challengeExpiresAt(ms)
-- ARGV[7] = 발급 간격(ms), ARGV[8] = 시간당 최대, ARGV[9] = 24시간 최대, ARGV[10] = 24시간 실패 최대
-- ARGV[11] = 1시간(ms), ARGV[12] = 24시간(ms), ARGV[13] = Challenge 키 접두사
-- ARGV[14] = 숫자 지문(보낼 4자리의 HMAC, 재발송 때 같은 숫자 방지용)
-- 새 OTP는 confirmed=0: 메일 발송 성공이 확인되기 전(otp_confirm_delivered.lua)에는 검증할 수 없다.
-- 반환: {1, version} 발급 / {-1, ms} 간격 / {-2, ms} 발급 수 / {-3, ms} 실패 합산 / {-9, 0} 새 키 충돌
-- 모든 검사를 쓰기 전에 끝낸다. 이전 Challenge 키는 현재 포인터에서 읽어 지운다(단일 Redis 전제).
local function pttl(key)
    local ttl = redis.call('PTTL', key)
    if ttl < 0 then return 0 end
    return ttl
end
local function count(key)
    return tonumber(redis.call('GET', key) or '0')
end
local function incr(key, ttl)
    if redis.call('INCR', key) == 1 then
        redis.call('PEXPIRE', key, ttl)
    end
end

if redis.call('EXISTS', KEYS[2]) == 1 then
    return {-1, pttl(KEYS[2])}
end
if count(KEYS[5]) >= tonumber(ARGV[10]) then
    return {-3, pttl(KEYS[5])}
end
if count(KEYS[3]) >= tonumber(ARGV[8]) then
    return {-2, pttl(KEYS[3])}
end
if count(KEYS[4]) >= tonumber(ARGV[9]) then
    return {-2, pttl(KEYS[4])}
end
if redis.call('EXISTS', KEYS[6]) == 1 then
    return {-9, 0}
end

incr(KEYS[3], ARGV[11])
incr(KEYS[4], ARGV[12])
redis.call('SET', KEYS[2], '1', 'PX', ARGV[7])
local previous = redis.call('GET', KEYS[1])
if previous then
    redis.call('DEL', ARGV[13] .. previous)
end
redis.call('HSET', KEYS[6], 'memberId', ARGV[2], 'version', '1', 'otpHmac', ARGV[3], 'keyId', ARGV[4],
        'attempts', '0', 'otpExpiresAt', ARGV[5], 'challengeExpiresAt', ARGV[6], 'confirmed', '0',
        'sentDigest', ARGV[14], 'prevSentDigest', '')
redis.call('PEXPIREAT', KEYS[6], ARGV[6])
redis.call('SET', KEYS[1], ARGV[1], 'PXAT', ARGV[6])
return {1, 1}
