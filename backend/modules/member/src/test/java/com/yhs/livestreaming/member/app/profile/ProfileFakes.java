package com.yhs.livestreaming.member.app.profile;

import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import com.yhs.livestreaming.member.app.session.RefreshRotation;
import com.yhs.livestreaming.member.app.session.RefreshTokenRecord;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** 회원 정보·공개 조회 단위 테스트용 대역(MB-D028, MB-D029). */
final class ProfileFakes {

    private ProfileFakes() {
    }

    static Member local(long id, String username, AccountStatus status) {
        Member member = Member.registerLocal(username, "hash", username + "@example.com", "닉네임" + id);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "accountStatus", status);
        ReflectionTestUtils.setField(member, "emailVerified", status == AccountStatus.ACTIVE);
        return member;
    }

    static Member google(long id, AccountStatus status) {
        Member member = Member.registerGoogle("google" + id + "@example.com", "구글" + id);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "accountStatus", status);
        ReflectionTestUtils.setField(member, "emailVerified", status == AccountStatus.ACTIVE);
        return member;
    }

    /** 실제 어댑터처럼 닉네임 변경은 ACTIVE 회원에만 반영된다. */
    static final class Members implements MemberRepository {
        final Map<Long, Member> byId = new LinkedHashMap<>();
        final List<Collection<Long>> batchCalls = new ArrayList<>();
        final List<String> nicknameUpdates = new ArrayList<>();
        /**
         * 비어 있지 않으면 UPDATE 호출마다 하나씩 꺼낸다(경합 재현용). 0이면 실제 판정 없이 0행을 돌려주고, 0이 아니면 실제
         * 판정·변경 경로를 그대로 탄다.
         */
        final java.util.Deque<Integer> forcedUpdateRows = new java.util.ArrayDeque<>();
        /** 닉네임 UPDATE를 판정하기 직전에 호출 순번(1부터)과 함께 실행한다(경합 재현용). */
        java.util.function.IntConsumer beforeNicknameUpdate;
        private int nicknameUpdateCalls;
        /** 닉네임 UPDATE가 반영된 직후 실행한다(경합 재현용). */
        Runnable afterNicknameUpdate;

        void add(Member member) {
            byId.put(member.getId(), member);
        }

        @Override
        public Optional<Member> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Member> findAllByIds(Collection<Long> ids) {
            batchCalls.add(List.copyOf(ids));
            return ids.stream().distinct().map(byId::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public int updateNicknameOfActive(long id, String nickname, Instant now) {
            nicknameUpdateCalls++;
            if (beforeNicknameUpdate != null) {
                beforeNicknameUpdate.accept(nicknameUpdateCalls);
            }
            if (!forcedUpdateRows.isEmpty() && forcedUpdateRows.pop() == 0) {
                return 0;
            }
            Member member = byId.get(id);
            if (member == null || member.getAccountStatus() != AccountStatus.ACTIVE) {
                return 0;
            }
            nicknameUpdates.add(id + ":" + nickname);
            ReflectionTestUtils.setField(member, "nickname", nickname);
            if (afterNicknameUpdate != null) {
                afterNicknameUpdate.run();
            }
            return 1;
        }

        @Override
        public boolean isUsernameTaken(String username, Instant expiredAtOrBefore) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLocalEmailTaken(String email, Instant expiredAtOrBefore) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Member saveReclaimingExpired(Member member, Instant expiredAtOrBefore) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Member save(Member member) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Member> findByUsername(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updatePasswordHash(long id, String expectedHash, String newHash, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int activatePending(long id, Instant createdAfter, Instant now) {
            throw new UnsupportedOperationException();
        }
    }

    /** 폐기 호출만 기록한다. */
    static final class Sessions implements LoginSessionStore {
        final List<String> revoked = new ArrayList<>();
        RuntimeException revokeFailure;

        @Override
        public RevokeResult revoke(String sessionId, long memberId) {
            if (revokeFailure != null) {
                throw revokeFailure;
            }
            revoked.add(sessionId + ":" + memberId);
            return RevokeResult.REVOKED;
        }

        @Override
        public CreateResult create(NewLoginSession session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RefreshTokenRecord> findRefresh(String refreshTokenHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RotateResult rotate(RefreshRotation rotation) {
            throw new UnsupportedOperationException();
        }
    }
}
