package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.member.app.cleanup.ExpiredPendingMemberStore;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.AccountType;
import com.yhs.livestreaming.member.domain.OAuthAccount;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 만료 PENDING 정리 JPA 어댑터(MB-D030). 배치 하나가 트랜잭션 하나다: 대상 ID를 ID 순서 커서로 고르고(OFFSET 없음), 삭제 문장
 * 안에서 계정 유형·PENDING·만료 조건을 다시 확인한다. 고른 뒤 활성화된 회원은 지우지 않는다.
 *
 * <p>GOOGLE은 OAuth 연결 행 → 회원 행 순서로 지운다(MB-D025, Google 재생성·활성화·이메일 갱신과 같은 잠금 순서). 스키마상 회원당
 * 연결 행이 하나로 보장되지 않으므로(member_id UNIQUE 없음) 연결 행 수가 아니라 중복 없는 회원 ID로 정합성을 본다: 연결 행을
 * 지운 대상 회원이 회원 행 삭제 뒤에도 남아 있으면 그 배치를 롤백해, 연결 행만 지워진 상태가 커밋되지 않게 한다. 다른
 * 트랜잭션(Google 재생성)이 같은 회원을 먼저 지워 삭제 수가 줄어든 것은 정상으로 본다.
 */
@Repository
public class ExpiredPendingMemberStoreAdapter implements ExpiredPendingMemberStore {

    private final MemberJpaRepository members;
    private final OAuthAccountJpaRepository links;
    private final TransactionTemplate tx;

    ExpiredPendingMemberStoreAdapter(MemberJpaRepository members, OAuthAccountJpaRepository links,
                                     PlatformTransactionManager transactionManager) {
        this.members = members;
        this.links = links;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public CleanupBatch deleteExpiredLocal(Instant expiredAtOrBefore, long afterId, int limit) {
        return tx.execute(status -> {
            List<Long> ids = candidates(AccountType.LOCAL, expiredAtOrBefore, afterId, limit);
            if (ids.isEmpty()) {
                return new CleanupBatch(0, afterId, 0);
            }
            int deleted = members.deleteExpiredPendingIn(ids, AccountType.LOCAL, AccountStatus.PENDING,
                    expiredAtOrBefore);
            return new CleanupBatch(ids.size(), ids.get(ids.size() - 1), deleted);
        });
    }

    @Override
    public CleanupBatch deleteExpiredGoogle(Instant expiredAtOrBefore, long afterId, int limit) {
        return tx.execute(status -> {
            List<Long> ids = candidates(AccountType.GOOGLE, expiredAtOrBefore, afterId, limit);
            if (ids.isEmpty()) {
                return new CleanupBatch(0, afterId, 0);
            }
            List<Long> eligible = members.findStillExpiredPendingIds(ids, AccountType.GOOGLE, AccountStatus.PENDING,
                    expiredAtOrBefore);
            int deleted = 0;
            if (!eligible.isEmpty()) {
                links.deleteLinksOf(OAuthAccount.GOOGLE, eligible);
                deleted = members.deleteExpiredPendingIn(eligible, AccountType.GOOGLE, AccountStatus.PENDING,
                        expiredAtOrBefore);
                // 다른 트랜잭션(Google 재생성 등)이 같은 회원을 먼저 지웠으면 삭제 수가 줄어도 정상이다. 문제는 연결 행을 지운
                // 회원이 아직 남은 경우(그 사이 상태가 바뀜)뿐이므로, 대상 중 남은 회원이 있을 때만 배치를 롤백한다.
                if (!members.findAllById(eligible).isEmpty()) {
                    throw new IllegalStateException("GOOGLE 정리 대상의 상태가 배치 중에 바뀌었다");
                }
            }
            return new CleanupBatch(ids.size(), ids.get(ids.size() - 1), deleted);
        });
    }

    private List<Long> candidates(AccountType type, Instant expiredAtOrBefore, long afterId, int limit) {
        return members.findExpiredPendingIds(type, AccountStatus.PENDING, expiredAtOrBefore, afterId, Limit.of(limit));
    }
}
