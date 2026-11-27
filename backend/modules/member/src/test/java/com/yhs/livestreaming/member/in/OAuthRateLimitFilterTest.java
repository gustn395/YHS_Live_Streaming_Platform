package com.yhs.livestreaming.member.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.yhs.livestreaming.member.app.limit.RateLimitScope;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** MB-T050 보완: 시작·콜백 제한 범위는 앱 내부 경로로 고른다(context path가 있어도 같음, MB-D024). */
class OAuthRateLimitFilterTest {

    private final List<RateLimitScope> scopes = new ArrayList<>();
    private final OAuthRateLimitFilter filter = new OAuthRateLimitFilter((scope, ip) -> scopes.add(scope),
            new OAuthRedirects(new MemberOAuthProperties("https://app.test", null, null,
                    new MemberOAuthProperties.Google("id", "secret", null, null, null, null))));

    @Test
    void startUsesTheOAuthStartScopeWithOrWithoutAContextPath() throws Exception {
        filter.doFilter(request("", MemberOAuthProperties.START_PATH), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request("/app", MemberOAuthProperties.START_PATH), new MockHttpServletResponse(),
                new MockFilterChain());
        assertThat(scopes).containsExactly(RateLimitScope.OAUTH_START, RateLimitScope.OAUTH_START);
    }

    @Test
    void percentEncodedStartPathIsStillTheOAuthStartScope() throws Exception { // 보안 체인 matcher와 같은 해석
        filter.doFilter(request("", "/api/v1/auth/oauth2/authorization/%67oogle"), new MockHttpServletResponse(),
                new MockFilterChain());
        assertThat(scopes).containsExactly(RateLimitScope.OAUTH_START);
    }

    @Test
    void callbackUsesTheLoginScope() throws Exception {
        filter.doFilter(request("/app", MemberOAuthProperties.CALLBACK_PATH), new MockHttpServletResponse(),
                new MockFilterChain());
        assertThat(scopes).containsExactly(RateLimitScope.LOGIN);
    }

    private static MockHttpServletRequest request(String contextPath, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", contextPath + path);
        request.setContextPath(contextPath);
        return request;
    }
}
