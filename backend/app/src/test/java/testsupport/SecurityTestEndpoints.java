package testsupport;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.auth.CurrentMember;
import com.yhs.livestreaming.global.security.PublicEndpoint;
import com.yhs.livestreaming.global.security.PublicEndpointContributor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 테스트 전용 엔드포인트. 운영 코드에는 없다.
 *
 * <p>애플리케이션 컴포넌트 스캔 범위({@code com.yhs.livestreaming}) 밖 패키지에 두어, 테스트 클래스에서
 * {@code @Import(SecurityTestEndpoints.class)}로 가져올 때만 등록된다. {@code /test/public/**}는 모듈이 공개 경로를
 * 등록하는 방식({@link PublicEndpointContributor})으로 공개한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SecurityTestEndpoints {

    @Bean
    PublicEndpointContributor testPublicEndpoints() {
        return () -> List.of(
                PublicEndpoint.of(HttpMethod.GET, "/test/public/**"),
                PublicEndpoint.of(HttpMethod.POST, "/test/public/**"));
    }

    public record EchoRequest(@NotBlank String name) {
    }

    // @Import로 이 설정을 가져오면 중첩 @RestController도 멤버 클래스로 함께 등록된다(따로 @Bean으로 만들지 않는다).
    @RestController
    public static class TestController {

        @GetMapping("/test/protected")
        String protectedResource() {
            return "protected";
        }

        @GetMapping("/test/protected/forbidden")
        String forbidden() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/test/protected/me")
        long protectedMe(@CurrentMember AuthenticatedMember member) {
            return member.memberId();
        }

        @GetMapping("/test/public/me")
        long publicMe(@CurrentMember AuthenticatedMember member) {
            return member.memberId();
        }

        @GetMapping("/test/public/denied")
        String publicDenied() {
            throw new AccessDeniedException("denied");
        }

        @PostMapping("/test/public/echo")
        String echo(@Valid @RequestBody EchoRequest request) {
            return request.name();
        }
    }
}
