package com.yhs.livestreaming.member.app.profile;

import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.api.MemberAccountStatus;
import com.yhs.livestreaming.member.app.api.MemberQueryApi;
import com.yhs.livestreaming.member.app.api.MemberSummary;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberQueryApi} 구현(MB-D029). member 내부에만 두고 다른 모듈에는 인터페이스로만 보인다(G-D015). */
@Service
@Transactional(readOnly = true)
class MemberQueryService implements MemberQueryApi {

    private final MemberRepository members;

    MemberQueryService(MemberRepository members) {
        this.members = members;
    }

    @Override
    public Optional<MemberSummary> findById(long memberId) {
        return members.findById(memberId).map(MemberQueryService::toSummary);
    }

    @Override
    public List<MemberSummary> findAllByIds(Collection<Long> memberIds) {
        Objects.requireNonNull(memberIds, "memberIds");
        if (memberIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("한 번에 조회할 수 있는 회원은 " + MAX_BATCH_SIZE + "명까지다: " + memberIds.size());
        }
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return members.findAllByIds(Set.copyOf(memberIds)).stream().map(MemberQueryService::toSummary).toList();
    }

    @Override
    public boolean isActive(long memberId) {
        return members.findById(memberId).map(member -> member.getAccountStatus() == AccountStatus.ACTIVE)
                .orElse(false);
    }

    private static MemberSummary toSummary(Member member) {
        return new MemberSummary(member.getId(), member.getNickname(), member.getRole().name(),
                toPublic(member.getAccountStatus()));
    }

    /** 내부 상태를 공개 상태로 옮긴다. 내부 상태가 늘면 컴파일 오류로 알 수 있게 switch로 둔다. */
    private static MemberAccountStatus toPublic(AccountStatus status) {
        return switch (status) {
            case PENDING -> MemberAccountStatus.PENDING;
            case ACTIVE -> MemberAccountStatus.ACTIVE;
            case DISABLED -> MemberAccountStatus.DISABLED;
        };
    }
}
