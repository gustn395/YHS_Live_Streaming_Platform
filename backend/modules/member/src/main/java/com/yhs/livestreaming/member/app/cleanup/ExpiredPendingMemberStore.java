package com.yhs.livestreaming.member.app.cleanup;

import java.time.Instant;

/**
 * 만료 PENDING 회원 정리 저장 포트(MB-D030). 호출 한 번이 배치 하나이고 자기 트랜잭션 하나다. 대상은 계정 유형·PENDING·
 * 가입 시각 {@code <= expiredAtOrBefore}이고 ID가 {@code afterId}보다 큰 회원을 ID 순서로 최대 {@code limit}명이다(OFFSET 없음).
 * 삭제 문장 안에서 조건을 다시 확인하므로, 대상으로 고른 뒤 활성화된 회원은 지우지 않는다.
 */
public interface ExpiredPendingMemberStore {

    /** LOCAL 만료 PENDING 배치 하나를 지운다. */
    CleanupBatch deleteExpiredLocal(Instant expiredAtOrBefore, long afterId, int limit);

    /**
     * GOOGLE 만료 PENDING 배치 하나를 OAuth 연결 행 → 회원 행 순서로 같은 트랜잭션에서 지운다(MB-D025). 연결 행을 지운 대상
     * 회원(중복 없는 회원 ID 기준) 중 지워지지 않고 남은 회원이 있으면(그 사이 상태가 바뀜) 그 배치 전체를 롤백하고
     * {@link IllegalStateException}을 던진다. 다른 트랜잭션이 같은 회원을 먼저 지운 경우는 정상이다.
     */
    CleanupBatch deleteExpiredGoogle(Instant expiredAtOrBefore, long afterId, int limit);

    /**
     * @param scanned 이번에 대상으로 고른 회원 수(삭제 수와 다를 수 있다: 경합으로 이미 지워졌거나 활성화된 회원)
     * @param lastId  고른 회원 중 가장 큰 ID(다음 배치의 커서). 고른 회원이 없으면 {@code afterId} 그대로
     * @param deleted 실제로 지운 회원 수
     */
    record CleanupBatch(int scanned, long lastId, int deleted) {
    }
}
