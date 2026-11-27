package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.limit.LoginAttemptLimiter;
import com.yhs.livestreaming.member.app.password.PasswordHasher;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** 로그인 서비스 단위 테스트 대역(외부 인프라 없음, G-D009). */
final class LoginFakes {

    private LoginFakes() {
    }

    static Member member(long id, String username, String passwordHash, AccountStatus status, Instant createdAt) {
        Member member = Member.registerLocal(username, passwordHash, username + "@example.com", "닉네임");
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "accountStatus", status);
        ReflectionTestUtils.setField(member, "createdAt", createdAt);
        return member;
    }

    static final class Members implements MemberRepository {
        final Map<String, Member> byUsername = new HashMap<>();
        final Map<Long, Member> byId = new HashMap<>();
        final List<String> rehashed = new ArrayList<>();
        RuntimeException rehashFailure;
        RuntimeException findFailure;
        Runnable onFind;
        int activateRows = -1; // -1: 실제처럼 PENDING·만료 전이면 1
        int activateCalls;
        Runnable onActivate;

        void add(Member member) {
            byUsername.put(member.getUsername(), member);
            byId.put(member.getId(), member);
        }

        @Override
        public boolean existsByUsername(String username) {
            return byUsername.containsKey(username);
        }

        @Override
        public boolean existsLocalEmail(String email) {
            return false;
        }

        @Override
        public Member save(Member member) {
            return member;
        }

        @Override
        public Optional<Member> findByUsername(String username) {
            return Optional.ofNullable(byUsername.get(username));
        }

        @Override
        public Optional<Member> findById(long id) {
            if (findFailure != null) {
                throw findFailure;
            }
            if (onFind != null) {
                onFind.run();
            }
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public int updatePasswordHash(long id, String expectedHash, String newHash, Instant now) {
            if (rehashFailure != null) {
                throw rehashFailure;
            }
            rehashed.add(newHash);
            return 1;
        }

        @Override
        public int activatePending(long id, Instant createdAfter, Instant now) {
            activateCalls++;
            Member member = byId.get(id);
            if (onActivate != null) {
                onActivate.run();
            }
            if (activateRows >= 0) {
                return activateRows;
            }
            if (member != null && member.getAccountStatus() == AccountStatus.PENDING
                    && member.getCreatedAt().isAfter(createdAfter)) {
                ReflectionTestUtils.setField(member, "accountStatus", AccountStatus.ACTIVE);
                ReflectionTestUtils.setField(member, "emailVerified", true);
                return 1;
            }
            return 0;
        }
    }

    /** "pw:<원문>" 형식 해시. 호출을 기록한다. */
    static final class Hasher implements PasswordHasher {
        final List<String> matched = new ArrayList<>();
        boolean needsRehash;

        @Override
        public String hash(String rawPassword) {
            return "pw2:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String storedHash) {
            matched.add(storedHash);
            return storedHash.equals("pw:" + rawPassword) || storedHash.equals("pw2:" + rawPassword);
        }

        @Override
        public boolean needsRehash(String storedHash) {
            return needsRehash;
        }
    }

    static final class Attempts implements LoginAttemptLimiter {
        final Map<String, Integer> failures = new HashMap<>();
        final List<String> successes = new ArrayList<>();
        Duration blocked;
        Duration blockedOnSuccess;

        @Override
        public Optional<Duration> blockedFor(String username) {
            return Optional.ofNullable(blocked);
        }

        @Override
        public FailureOutcome recordFailure(String username) {
            int count = failures.merge(username, 1, Integer::sum);
            return count >= 10 ? new FailureOutcome(true, Duration.ofMinutes(15))
                    : new FailureOutcome(false, Duration.ZERO);
        }

        @Override
        public Optional<Duration> recordSuccess(String username) {
            if (blockedOnSuccess != null) {
                return Optional.of(blockedOnSuccess);
            }
            successes.add(username);
            failures.remove(username);
            return Optional.empty();
        }
    }
}
