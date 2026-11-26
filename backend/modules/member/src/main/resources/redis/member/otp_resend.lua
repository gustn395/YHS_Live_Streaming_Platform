-- 같은 Challenge 안 OTP 재발송(MB-D015, MB-D017).
-- KEYS[1] = auth:otp:challenge:{challengeId}, KEYS[2] = auth:otp:member:{id}:current
-- KEYS[3] = last-issued, KEYS[4] = issue:1h, KEYS[5] = issue:24h, KEYS[6] = fail:24h
-- ARGV[1] = challengeId, ARGV[2] = memberId, ARGV[3] = 기대 version, ARGV[4] = 새 otpHmac, ARGV[5] = keyId
-- ARGV[6] = now(ms), ARGV[7] = 새 otpExpiresAt(ms, Challenge 만료를 넘지 않게 Java가 계산)
-- ARGV[8] = 발급 간격(ms), ARGV[9] = 시간당 최대, ARGV[10] = 24시간 최대, ARGV[11] = 24시간 실패 최대
-- ARGV[12] = 1시간(ms), ARGV[13] = 24시간(ms), ARGV[14] = 새 숫자 지문
-- 숫자 지문은 최근 두 개(sentDigest = 이번, prevSentDigest = 직전)를 남긴다. 새 OTP는 confirmed=0(발송 확인 전 검증 불가).
-- 반환: {1, 새 version} / {0, 0} Challenge 무효 / {-1|-2|-3, ms} 제한 / {-4, 0} version 경합
-- Challenge 키의 만료(절대 10분)는 바꾸지 않는다.
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

local challenge = redis.call('HMGET', KEYS[1], 'memberId', 'version', 'challengeExpiresAt')
if not challenge[1] or challenge[1] ~= ARGV[2] or redis.call('GET', KEYS[2]) ~= ARGV[1]
        or tonumber(challenge[3]) <= tonumber(ARGV[6]) then
    return {0, 0}
end
if redis.call('EXISTS', KEYS[3]) == 1 then
    return {-1, pttl(KEYS[3])}
end
if count(KEYS[6]) >= tonumber(ARGV[11]) then
    return {-3, pttl(KEYS[6])}
end
if count(KEYS[4]) >= tonumber(ARGV[9]) then
    return {-2, pttl(KEYS[4])}
end
if count(KEYS[5]) >= tonumber(ARGV[10]) then
    return {-2, pttl(KEYS[5])}
end
if challenge[2] ~= ARGV[3] then
    return {-4, 0}
end

incr(KEYS[4], ARGV[12])
incr(KEYS[5], ARGV[13])
redis.call('SET', KEYS[3], '1', 'PX', ARGV[8])
local version = tonumber(challenge[2]) + 1
local sent = redis.call('HGET', KEYS[1], 'sentDigest') or ''
redis.call('HSET', KEYS[1], 'version', tostring(version), 'otpHmac', ARGV[4], 'keyId', ARGV[5], 'attempts', '0',
        'otpExpiresAt', ARGV[7], 'confirmed', '0', 'prevSentDigest', sent, 'sentDigest', ARGV[14])
return {1, version}
