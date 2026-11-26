package testsupport;

import com.yhs.livestreaming.member.app.password.BreachedPasswordChecker;

/**
 * app 통합 테스트용 유출 비밀번호 검사 대역(MB-D010). 실제 HIBP를 부르지 않는다. 기본 결과는 통과이고, 테스트가
 * {@link #willReturn}으로 바꾼 뒤 {@link #reset}으로 되돌린다. {@link AppIntegrationTest}가 등록한다.
 */
public class StubBreachedPasswordChecker implements BreachedPasswordChecker {

    private volatile Result next = Result.NOT_BREACHED;

    @Override
    public Result check(String rawPassword) {
        return next;
    }

    public void willReturn(Result result) {
        this.next = result;
    }

    public void reset() {
        this.next = Result.NOT_BREACHED;
    }
}
