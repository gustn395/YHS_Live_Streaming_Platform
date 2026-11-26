-- OTP 검증과 소비(MB-D015, MB-D017).
-- KEYS[1] = auth:otp:challenge:{challengeId}, KEYS[2] = auth:otp:member:{id}:current, KEYS[3] = fail:24h
-- ARGV[1] = challengeId, ARGV[2] = memberId, ARGV[3] = 기대 version, ARGV[4] = 입력값의 HMAC
-- ARGV[5] = now(ms), ARGV[6] = OTP당 최대 시도, ARGV[7] = 24시간 실패 최대, ARGV[8] = 24시간(ms)
-- 반환: {1, memberId} 성공(소비) / {0, 0} Challenge 무효 / {-3, ms} 실패 합산 제한 / {-4, 0} version 경합
--       {-5, 0} OTP 만료·무효·발송 미확인 / {-6, 남은 시도} 불일치 / {-7, 0} 이번 불일치로 시도 소진
-- 검증·실패 증가·무효화·소비를 한 번에 한다. version이 다르면(재발송과 경합) 실패로 세지 않는다.
local function pttl(key)
    local ttl = redis.call('PTTL', key)
    if ttl < 0 then return 0 end
    return ttl
end

local c = redis.call('HMGET', KEYS[1], 'memberId', 'version', 'otpHmac', 'attempts', 'otpExpiresAt',
        'challengeExpiresAt', 'confirmed')
if not c[1] or c[1] ~= ARGV[2] or redis.call('GET', KEYS[2]) ~= ARGV[1]
        or tonumber(c[6]) <= tonumber(ARGV[5]) then
    return {0, 0}
end
if tonumber(redis.call('GET', KEYS[3]) or '0') >= tonumber(ARGV[7]) then
    return {-3, pttl(KEYS[3])}
end
if c[2] ~= ARGV[3] then
    return {-4, 0}
end
-- 무효·만료이거나 메일 발송 성공이 확인되지 않은 OTP는 검증하지 않는다(실패로 세지 않음, MB-D016)
if c[3] == '' or c[7] ~= '1' or tonumber(c[5]) <= tonumber(ARGV[5]) then
    return {-5, 0}
end
if c[3] == ARGV[4] then
    redis.call('DEL', KEYS[1])
    redis.call('DEL', KEYS[2])
    return {1, tonumber(c[1])}
end

local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
if redis.call('INCR', KEYS[3]) == 1 then
    redis.call('PEXPIRE', KEYS[3], ARGV[8])
end
if attempts >= tonumber(ARGV[6]) then
    redis.call('HSET', KEYS[1], 'otpHmac', '')
    return {-7, 0}
end
return {-6, tonumber(ARGV[6]) - attempts}
