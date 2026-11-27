package com.yhs.livestreaming.member.app.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yhs.livestreaming.member.app.api.MemberAccountStatus;
import com.yhs.livestreaming.member.app.api.MemberQueryApi;
import com.yhs.livestreaming.member.app.api.MemberSummary;
import com.yhs.livestreaming.member.domain.AccountStatus;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/** MB-T058·MB-T059: 다른 모듈용 회원 조회 공개 인터페이스(MB-D029). */
class MemberQueryServiceTest {

    private final ProfileFakes.Members members = new ProfileFakes.Members();
    private final MemberQueryApi api = new MemberQueryService(members);

    @Test
    void findByIdReturnsSummaryOrEmpty() { // MB-T058
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));

        assertThat(api.findById(1L)).contains(new MemberSummary(1L, "닉네임1", "USER", MemberAccountStatus.ACTIVE));
        assertThat(api.findById(2L)).isEmpty();
    }

    @Test
    void findAllByIdsSkipsMissingIdsAndDuplicates() { // MB-T058
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));
        members.add(ProfileFakes.google(2L, AccountStatus.PENDING));
        members.add(ProfileFakes.local(3L, "tester03", AccountStatus.DISABLED));

        List<MemberSummary> found = api.findAllByIds(List.of(3L, 1L, 99L, 1L, 2L));

        assertThat(found).extracting(MemberSummary::memberId).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(found).filteredOn(summary -> summary.memberId() == 2L).singleElement()
                .satisfies(summary -> assertThat(summary.accountStatus()).isEqualTo(MemberAccountStatus.PENDING));
        assertThat(found).filteredOn(summary -> summary.memberId() == 3L).singleElement()
                .satisfies(summary -> assertThat(summary.accountStatus()).isEqualTo(MemberAccountStatus.DISABLED));
    }

    @Test
    void findAllByIdsAcceptsUpToOneHundredAndRejectsMore() { // MB-T058
        List<Long> hundred = LongStream.rangeClosed(1, 100).boxed().toList();
        List<Long> hundredOne = LongStream.rangeClosed(1, 101).boxed().toList();

        assertThat(api.findAllByIds(hundred)).isEmpty();
        assertThatThrownBy(() -> api.findAllByIds(hundredOne)).isInstanceOf(IllegalArgumentException.class);
        assertThat(api.findAllByIds(List.of())).isEmpty();
        assertThat(members.batchCalls).hasSize(1); // 빈 목록·초과는 저장소를 부르지 않는다
        assertThat(MemberQueryApi.MAX_BATCH_SIZE).isEqualTo(100);
    }

    @Test
    void onlyActiveMembersAreActive() { // MB-T058
        members.add(ProfileFakes.local(1L, "tester01", AccountStatus.ACTIVE));
        members.add(ProfileFakes.local(2L, "tester02", AccountStatus.PENDING));
        members.add(ProfileFakes.local(3L, "tester03", AccountStatus.DISABLED));

        assertThat(api.isActive(1L)).isTrue();
        assertThat(api.isActive(2L)).isFalse();
        assertThat(api.isActive(3L)).isFalse();
        assertThat(api.isActive(99L)).isFalse();
    }

    @Test
    void everyInternalStatusHasAPublicStatus() { // MB-T059: 내부 상태가 늘면 공개 상태 매핑도 함께 바꿔야 한다
        long id = 1;
        for (AccountStatus status : AccountStatus.values()) {
            members.add(ProfileFakes.local(id, "tester" + id, status));
            assertThat(api.findById(id)).get().extracting(MemberSummary::accountStatus).asString()
                    .isEqualTo(status.name());
            id++;
        }
    }

    @Test
    void summaryCarriesNoEmailOrUsername() { // MB-T059
        assertThat(Arrays.stream(MemberSummary.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("memberId", "nickname", "role", "accountStatus");
    }
}
