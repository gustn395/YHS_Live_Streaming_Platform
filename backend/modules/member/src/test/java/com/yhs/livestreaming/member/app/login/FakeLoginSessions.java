package com.yhs.livestreaming.member.app.login;

import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.app.session.NewLoginSession;
import com.yhs.livestreaming.member.app.session.RefreshRotation;
import com.yhs.livestreaming.member.app.session.RefreshTokenRecord;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 메모리 로그인 세션 저장소(단위 테스트 대역, G-D009). Redis Lua와 같은 규칙(현재 해시 비교·재사용 시 세션 폐기·소유자 확인)을
 * 단순하게 흉내 내고, 호출 기록과 장애 주입을 제공한다. 실제 원자성은 Redis 통합 테스트가 확인한다.
 */
class FakeLoginSessions implements LoginSessionStore {

    record Session(long memberId, String refreshHash, Instant expiresAt) {
    }

    record Refresh(String sid, long memberId) {
    }

    final Map<String, Session> sessions = new HashMap<>();
    final Map<String, Refresh> refreshes = new HashMap<>();
    final List<RefreshRotation> rotations = new ArrayList<>();
    final List<String> revoked = new ArrayList<>();
    final Deque<RotateResult> forcedRotateResults = new ArrayDeque<>();
    final Set<String> failRevokeFor = new HashSet<>();
    RuntimeException findFailure;
    RuntimeException rotateFailure;

    void put(String sid, long memberId, String refreshHash, Instant expiresAt) {
        sessions.put(sid, new Session(memberId, refreshHash, expiresAt));
        refreshes.put(refreshHash, new Refresh(sid, memberId));
    }

    @Override
    public CreateResult create(NewLoginSession session) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<RefreshTokenRecord> findRefresh(String refreshTokenHash) {
        if (findFailure != null) {
            throw findFailure;
        }
        Refresh refresh = refreshes.get(refreshTokenHash);
        if (refresh == null) {
            return Optional.empty();
        }
        Session session = sessions.get(refresh.sid());
        if (session == null || session.memberId() != refresh.memberId()) {
            return Optional.empty();
        }
        return Optional.of(new RefreshTokenRecord(refresh.sid(), refresh.memberId(), session.expiresAt(),
                session.refreshHash().equals(refreshTokenHash)));
    }

    @Override
    public RotateResult rotate(RefreshRotation rotation) {
        rotations.add(rotation);
        if (rotateFailure != null) {
            throw rotateFailure;
        }
        if (!forcedRotateResults.isEmpty()) {
            return forcedRotateResults.pop();
        }
        Session session = sessions.get(rotation.sessionId());
        if (session == null || session.memberId() != rotation.memberId()) {
            return RotateResult.INVALID;
        }
        if (!session.refreshHash().equals(rotation.currentHash())) {
            sessions.remove(rotation.sessionId());
            return RotateResult.REUSED;
        }
        sessions.put(rotation.sessionId(), new Session(session.memberId(), rotation.newHash(), session.expiresAt()));
        refreshes.put(rotation.newHash(), new Refresh(rotation.sessionId(), rotation.memberId()));
        return RotateResult.ROTATED;
    }

    @Override
    public RevokeResult revoke(String sessionId, long memberId) {
        if (failRevokeFor.contains(sessionId)) {
            throw new com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException("injected", null);
        }
        Session session = sessions.get(sessionId);
        if (session == null) {
            return RevokeResult.NOT_FOUND;
        }
        if (session.memberId() != memberId) {
            return RevokeResult.MEMBER_MISMATCH;
        }
        sessions.remove(sessionId);
        revoked.add(sessionId);
        return RevokeResult.REVOKED;
    }
}
