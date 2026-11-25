package com.yhs.livestreaming.ci;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Step 4 Q5 검증용 임시 테스트다. 테스트가 실패하면 backend-build가 실패하고 main 병합이 막히는지 확인한다.
 * 확인이 끝나면 되돌림 커밋으로 삭제한다. main에는 남지 않는다.
 */
class CiFailureVerificationTest {

    @Test
    void intentionallyFailsToVerifyCiBlocksMerge() {
        assertThat(1 + 1)
                .as("Step 4 Q5 의도적 실패: 되돌림 커밋으로 삭제할 임시 테스트")
                .isEqualTo(3);
    }
}
