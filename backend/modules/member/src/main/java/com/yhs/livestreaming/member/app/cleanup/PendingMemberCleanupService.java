package com.yhs.livestreaming.member.app.cleanup;

import com.yhs.livestreaming.member.app.cleanup.ExpiredPendingMemberStore.CleanupBatch;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 만료 PENDING 회원 정리(MB-W16, MB-D030). 실행 시작 때 앱 Clock으로 기준 시각(지금 - 24시간)을 한 번 정하고, LOCAL과 GOOGLE을
 * 각각 ID 커서로 배치 크기만큼씩 지운다. 이 메서드에는 트랜잭션이 없고 배치마다 저장 포트가 자기 트랜잭션을 연다(자기 호출로
 * 트랜잭션이 빠지는 문제 없음).
 *
 * <p>끝 판정은 "고른 대상 수가 배치 크기보다 작다"로 한다. 지운 수로 판정하지 않는다: 경합으로 일부가 이미 지워졌거나 활성화되면
 * 지운 수가 작아도 뒤에 대상이 남아 있을 수 있다. 커서는 고른 대상의 마지막 ID로 나아가므로 0행이 반복돼도 끝난다.
 *
 * <p>배치가 실패하면 그 배치만 롤백되고(이전 배치는 커밋됨) 그 유형은 이번 실행에서 멈춘다. 남은 대상은 다음 주기에 처리된다.
 * 로그에는 커밋된 삭제 수·소요 시간·예외 종류만 남기고 회원 ID·username·이메일·OAuth sub·예외 메시지는 남기지 않는다.
 */
@Service
public class PendingMemberCleanupService {

    private static final Logger log = LoggerFactory.getLogger(PendingMemberCleanupService.class);

    private final ExpiredPendingMemberStore store;
    private final Clock clock;
    private final MemberCleanupProperties properties;

    public PendingMemberCleanupService(ExpiredPendingMemberStore store, Clock clock,
                                       MemberCleanupProperties properties) {
        this.store = store;
        this.clock = clock;
        this.properties = properties;
    }

    public CleanupResult cleanupExpired() {
        long started = System.nanoTime();
        Instant expiredAtOrBefore = clock.instant().minus(Member.PENDING_TTL);
        Drain local = drain("LOCAL", (after, limit) -> store.deleteExpiredLocal(expiredAtOrBefore, after, limit));
        Drain google = drain("GOOGLE", (after, limit) -> store.deleteExpiredGoogle(expiredAtOrBefore, after, limit));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        boolean completed = local.completed && google.completed;
        if (completed) {
            log.info("만료 PENDING 정리: local={}, google={}, elapsedMs={}, completed=true", local.deleted,
                    google.deleted, elapsed.toMillis());
        } else {
            log.warn("만료 PENDING 정리 일부 실패: local={}, google={}, elapsedMs={}, completed=false", local.deleted,
                    google.deleted, elapsed.toMillis());
        }
        return new CleanupResult(local.deleted, google.deleted, completed, elapsed);
    }

    private Drain drain(String type, BatchCall call) {
        int batchSize = properties.batchSize();
        Drain drain = new Drain();
        long afterId = 0L;
        while (true) {
            CleanupBatch batch;
            try {
                batch = call.run(afterId, batchSize);
            } catch (RuntimeException ex) {
                drain.completed = false;
                log.warn("만료 PENDING 정리 배치 실패: type={}, committedDeleted={}, error={}", type, drain.deleted,
                        ex.getClass().getSimpleName());
                return drain;
            }
            drain.deleted += batch.deleted();
            if (batch.scanned() < batchSize) {
                return drain;
            }
            if (batch.lastId() <= afterId) { // 커서가 나아가지 않으면 같은 배치를 무한히 반복하게 된다
                drain.completed = false;
                log.warn("만료 PENDING 정리 커서 정체: type={}, committedDeleted={}", type, drain.deleted);
                return drain;
            }
            afterId = batch.lastId();
        }
    }

    @FunctionalInterface
    private interface BatchCall {
        CleanupBatch run(long afterId, int limit);
    }

    private static final class Drain {
        int deleted;
        boolean completed = true;
    }
}
