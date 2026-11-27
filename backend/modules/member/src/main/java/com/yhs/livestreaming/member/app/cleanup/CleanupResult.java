package com.yhs.livestreaming.member.app.cleanup;

import java.time.Duration;

/**
 * 정리 한 번의 결과(MB-D030). 삭제 수는 실제로 커밋된 배치만 센다.
 *
 * @param completed 두 유형 모두 실패 없이 끝났는지(거짓이면 남은 대상은 다음 주기에 처리된다)
 */
public record CleanupResult(int localDeleted, int googleDeleted, boolean completed, Duration elapsed) {
}
