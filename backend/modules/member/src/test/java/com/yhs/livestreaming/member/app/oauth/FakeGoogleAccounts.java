package com.yhs.livestreaming.member.app.oauth;

import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 메모리 GOOGLE 회원 저장소(단위 테스트 대역, G-D009). 실제 트랜잭션·동시성은 {@code GoogleAccountRepositoryAdapterTest}가 맡는다.
 */
public class FakeGoogleAccounts implements GoogleAccountRepository {

    public final Map<String, Member> bySub = new HashMap<>();
    public final Map<Long, String> linkEmail = new HashMap<>();
    public final List<String> calls = new ArrayList<>();
    public final Deque<Runnable> beforeCreate = new ArrayDeque<>();
    public RuntimeException findFailure;
    private long nextId = 100;

    public Member add(String sub, Member member, long id, Instant createdAt) {
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", createdAt);
        bySub.put(sub, member);
        linkEmail.put(id, member.getEmail());
        return member;
    }

    @Override
    public Optional<Member> findBySub(String sub) {
        calls.add("find");
        if (findFailure != null) {
            throw findFailure;
        }
        return Optional.ofNullable(bySub.get(sub));
    }

    @Override
    public Member create(Member newMember, String sub, String email) {
        calls.add("create");
        if (!beforeCreate.isEmpty()) {
            beforeCreate.pop().run();
        }
        if (bySub.containsKey(sub)) {
            throw new GoogleLinkConflictException();
        }
        return add(sub, newMember, nextId++, Instant.now());
    }

    @Override
    public Optional<Member> replaceExpiredPending(long expiredMemberId, String sub, Instant expiredAtOrBefore,
                                                  Member newMember, String email) {
        calls.add("replace");
        Member current = bySub.get(sub);
        if (current == null || current.getId() != expiredMemberId || current.getAccountStatus() != AccountStatus.PENDING
                || current.getCreatedAt().isAfter(expiredAtOrBefore)) {
            return Optional.empty();
        }
        linkEmail.remove(expiredMemberId);
        return Optional.of(add(sub, newMember, nextId++, Instant.now()));
    }

    @Override
    public int activatePending(long memberId, String email, Instant createdAfter, Instant now) {
        calls.add("activate:" + email);
        for (Member m : bySub.values()) {
            if (m.getId() == memberId && m.getAccountStatus() == AccountStatus.PENDING
                    && m.getCreatedAt().isAfter(createdAfter)) {
                ReflectionTestUtils.setField(m, "accountStatus", AccountStatus.ACTIVE);
                ReflectionTestUtils.setField(m, "emailVerified", true);
                ReflectionTestUtils.setField(m, "email", email);
                linkEmail.put(memberId, email);
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int updateActiveEmail(long memberId, String email, Instant now) {
        calls.add("email:" + email);
        for (Member m : bySub.values()) {
            if (m.getId() == memberId && m.getAccountStatus() == AccountStatus.ACTIVE) {
                ReflectionTestUtils.setField(m, "email", email);
                linkEmail.put(memberId, email);
                return 1;
            }
        }
        return 0;
    }
}
