-- OTP 메일 발송 성공 확인(MB-D016). 확인 전 OTP는 검증할 수 없다(otp_verify.lua).
-- KEYS[1] = auth:otp:challenge:{challengeId}
-- ARGV[1] = 발송한 version
-- 반환: 1 확인함 / 0 Challenge 없음·version이 바뀜(그 OTP는 확인되지 않은 채 남아 검증되지 않는다)
if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1] then
    return 0
end
redis.call('HSET', KEYS[1], 'confirmed', '1')
return 1
