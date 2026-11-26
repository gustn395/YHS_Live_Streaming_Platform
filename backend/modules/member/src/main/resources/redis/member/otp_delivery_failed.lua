-- OTP 메일 발송 실패 보상(MB-D016).
-- KEYS[1] = auth:otp:challenge:{challengeId}, KEYS[2] = auth:otp:member:{id}:current
-- ARGV[1] = challengeId, ARGV[2] = 발송한 version, ARGV[3] = 'LOGIN' | 'RESEND'
-- 반환: 1 보상함 / 0 이미 바뀜(다른 version·새 Challenge는 건드리지 않음)
-- 발급 카운터와 60초 간격은 되돌리지 않는다.
if redis.call('HGET', KEYS[1], 'version') ~= ARGV[2] then
    return 0
end
if ARGV[3] == 'LOGIN' then
    redis.call('DEL', KEYS[1])
    if redis.call('GET', KEYS[2]) == ARGV[1] then
        redis.call('DEL', KEYS[2])
    end
else
    -- 이 OTP는 무효로 한다. 숫자 지문은 남긴다(SMTP 타임아웃이면 실제로는 전달됐을 수 있다)
    redis.call('HSET', KEYS[1], 'otpHmac', '')
end
return 1
