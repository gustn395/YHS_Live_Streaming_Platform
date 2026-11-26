-- 로그인 세션 + Refresh Token 해시 원자 저장(MB-D014).
-- KEYS[1] = auth:session:{sid}, KEYS[2] = auth:refresh:{sha256(refresh)}
-- ARGV[1] = memberId, ARGV[2] = refreshHash, ARGV[3] = sid, ARGV[4] = expiresAt(epoch millis)
-- 반환: 1 저장함, 0 키 충돌(아무것도 쓰지 않음)
-- 모든 확인을 쓰기 전에 끝낸다. 쓰기 단계의 명령은 자료형이 정해진 새 키에만 쓰므로 중간 실패로 부분 저장될 여지를 줄인다.
if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
    return 0
end
redis.call('HSET', KEYS[1], 'memberId', ARGV[1], 'refreshHash', ARGV[2], 'expiresAt', ARGV[4])
redis.call('PEXPIREAT', KEYS[1], ARGV[4])
redis.call('HSET', KEYS[2], 'sid', ARGV[3], 'memberId', ARGV[1], 'expiresAt', ARGV[4])
redis.call('PEXPIREAT', KEYS[2], ARGV[4])
return 1
