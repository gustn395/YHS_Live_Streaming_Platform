package com.yhs.livestreaming.member.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.yhs.livestreaming.member.app.oauth.OAuthStateStore;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** MB-T048 보완: 형식이 틀린 {@code __Host-yhs_oauth} 쿠키는 조회·소비 모두 저장소에 묻지 않는다(MB-D024). */
class OAuthCookieAuthorizationRequestRepositoryTest {

    private final AtomicInteger storeCalls = new AtomicInteger();
    private final OAuthStateStore store = new OAuthStateStore() {
        @Override
        public void save(String handle, Map<String, String> fields, Duration ttl) {
            storeCalls.incrementAndGet();
        }

        @Override
        public Optional<Map<String, String>> find(String handle, String expectedState) {
            storeCalls.incrementAndGet();
            return Optional.empty();
        }

        @Override
        public Optional<Map<String, String>> consume(String handle, String expectedState) {
            storeCalls.incrementAndGet();
            return Optional.empty();
        }
    };
    private final OAuthCookieAuthorizationRequestRepository repository =
            new OAuthCookieAuthorizationRequestRepository(store, new AuthCookieWriter(Clock.systemUTC()));

    @Test
    void malformedCookieIsNeitherLoadedNorConsumed() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", MemberOAuthProperties.CALLBACK_PATH);
        request.setCookies(new Cookie(MemberAuthCookies.OAUTH_STATE, "not a token"));
        request.setParameter("state", "state-value");

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
        assertThat(repository.removeAuthorizationRequest(request, new MockHttpServletResponse())).isNull();
        assertThat(storeCalls).hasValue(0);
    }
}
