package com.yhs.livestreaming.member.app.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.yhs.livestreaming.member.app.cleanup.ExpiredPendingMemberStore.CleanupBatch;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** MB-T061·MB-T062(서비스): 만료 PENDING 정리 배치의 기준 시각·반복·실패 처리·기록(MB-D030). 저장소는 대역이다. */
@ExtendWith(OutputCaptureExtension.class)
class PendingMemberCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofHours(24));

    private final FakeStore store = new FakeStore();

    private PendingMemberCleanupService service(int batchSize) {
        return new PendingMemberCleanupService(store, Clock.fixed(NOW, ZoneOffset.UTC),
                new MemberCleanupProperties(true, Duration.ofHours(1), batchSize));
    }

    @Test
    void cutoffIsComputedOnceAsExactly24HoursBeforeStart() { // MB-T061
        store.local.add(new CleanupBatch(2, 7L, 2));
        store.google.add(new CleanupBatch(0, 0L, 0));

        service(2).cleanupExpired();

        assertThat(store.cutoffs).isNotEmpty().allMatch(CUTOFF::equals);
    }

    @Test
    void keepsGoingWhileABatchIsFullUsingTheLastIdAsCursor() { // MB-T061: OFFSET 없이 ID 커서
        store.local.add(new CleanupBatch(3, 10L, 3));
        store.local.add(new CleanupBatch(3, 25L, 1)); // 경합으로 일부만 지워져도 꽉 찬 배치면 계속
        store.local.add(new CleanupBatch(3, 31L, 0)); // 0행이어도 커서는 나아간다
        store.local.add(new CleanupBatch(1, 40L, 1)); // 덜 찬 배치면 끝
        store.google.add(new CleanupBatch(0, 0L, 0));

        CleanupResult result = service(3).cleanupExpired();

        assertThat(store.localAfterIds).containsExactly(0L, 10L, 25L, 31L);
        assertThat(store.limits).allMatch(limit -> limit == 3);
        assertThat(result.localDeleted()).isEqualTo(5);
        assertThat(result.googleDeleted()).isZero();
        assertThat(result.completed()).isTrue();
    }

    @Test
    void cleansLocalAndGoogleSeparately() { // MB-T061
        store.local.add(new CleanupBatch(1, 3L, 1));
        store.google.add(new CleanupBatch(2, 9L, 2));
        store.google.add(new CleanupBatch(0, 9L, 0));

        CleanupResult result = service(2).cleanupExpired();

        assertThat(result.localDeleted()).isEqualTo(1);
        assertThat(result.googleDeleted()).isEqualTo(2);
        assertThat(store.googleAfterIds).containsExactly(0L, 9L);
    }

    @Test
    void failedBatchStopsThatTypeKeepsCommittedCountsAndLetsTheOtherTypeRun(CapturedOutput output) { // MB-T062
        store.local.add(new CleanupBatch(2, 5L, 2));
        store.localFailure = new IllegalStateException("member 42 tester@example.com google-sub-1");
        store.google.add(new CleanupBatch(1, 8L, 1));

        CleanupResult result = service(2).cleanupExpired();

        assertThat(result.localDeleted()).isEqualTo(2); // 실패 전에 커밋된 배치
        assertThat(result.googleDeleted()).isEqualTo(1);
        assertThat(result.completed()).isFalse();
        assertThat(output.getAll()).contains("WARN").contains("IllegalStateException")
                .doesNotContain("tester@example.com").doesNotContain("google-sub-1").doesNotContain("member 42");
        // 부분 실패 요약은 WARN 한 줄에 커밋된 LOCAL·GOOGLE 건수와 소요 시간을 함께 남긴다
        assertThat(output.getAll().lines().filter(line -> line.contains("WARN") && line.contains("local=2")
                && line.contains("google=1") && line.contains("elapsedMs="))).hasSize(1);
    }

    @Test
    void stopsWhenTheCursorDoesNotAdvance() { // MB-T061: 저장소가 커서를 못 움직이면 무한 반복하지 않는다
        store.local.add(new CleanupBatch(2, 0L, 0));
        store.local.add(new CleanupBatch(2, 0L, 0));

        CleanupResult result = service(2).cleanupExpired();

        assertThat(store.localAfterIds).containsExactly(0L);
        assertThat(result.completed()).isFalse();
    }

    @Test
    void logsOnlyCountsAndElapsedTime(CapturedOutput output) { // MB-T062: 개인정보 없음
        store.local.add(new CleanupBatch(1, 123456L, 1));
        store.google.add(new CleanupBatch(0, 0L, 0));

        service(2).cleanupExpired();

        assertThat(output.getAll()).contains("local=1").contains("google=0").contains("elapsedMs=")
                .doesNotContain("123456");
    }

    /** 미리 넣은 배치 결과를 차례로 돌려주고 호출 인자를 기록한다. */
    private static final class FakeStore implements ExpiredPendingMemberStore {
        final Deque<CleanupBatch> local = new ArrayDeque<>();
        final Deque<CleanupBatch> google = new ArrayDeque<>();
        final List<Instant> cutoffs = new ArrayList<>();
        final List<Long> localAfterIds = new ArrayList<>();
        final List<Long> googleAfterIds = new ArrayList<>();
        final List<Integer> limits = new ArrayList<>();
        RuntimeException localFailure;

        @Override
        public CleanupBatch deleteExpiredLocal(Instant expiredAtOrBefore, long afterId, int limit) {
            record(expiredAtOrBefore, limit);
            localAfterIds.add(afterId);
            if (local.isEmpty() && localFailure != null) {
                throw localFailure;
            }
            return local.isEmpty() ? new CleanupBatch(0, afterId, 0) : local.pop();
        }

        @Override
        public CleanupBatch deleteExpiredGoogle(Instant expiredAtOrBefore, long afterId, int limit) {
            record(expiredAtOrBefore, limit);
            googleAfterIds.add(afterId);
            return google.isEmpty() ? new CleanupBatch(0, afterId, 0) : google.pop();
        }

        private void record(Instant cutoff, int limit) {
            cutoffs.add(cutoff);
            limits.add(limit);
        }
    }
}
