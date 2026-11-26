package testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 통합 테스트에서 인증된 회원 요청을 만든다. 실제 로그인(JWT)은 MB-W05 이후에 생긴다. */
public final class TestAuthentications {

    private TestAuthentications() {
    }

    public static RequestPostProcessor member(long memberId) {
        AuthenticatedMember principal = new AuthenticatedMember(memberId, "USER", "test-session-" + memberId);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
