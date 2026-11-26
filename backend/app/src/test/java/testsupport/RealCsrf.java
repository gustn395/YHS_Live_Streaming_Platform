package testsupport;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 실제 초기 발급 API({@code GET /api/v1/auth/csrf})로 받은 CSRF 토큰을 요청에 붙인다(G-D014).
 * spring-security-test의 {@code csrf()}는 공유 테스트 컨텍스트의 CSRF 저장소를 테스트용으로 바꿔 두므로 쓰지 않는다.
 */
public final class RealCsrf {

    public static final String COOKIE = "XSRF-TOKEN";
    public static final String HEADER = "X-XSRF-TOKEN";

    private RealCsrf() {
    }

    /** 발급 API를 호출해 쿠키 값을 돌려준다. */
    public static String issue(MockMvc mockMvc) throws Exception {
        Cookie cookie = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/csrf")).andReturn().getResponse()
                .getCookie(COOKIE);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            throw new AssertionError("CSRF 쿠키를 받지 못했다");
        }
        return cookie.getValue();
    }

    /** 같은 토큰을 쿠키와 헤더로 붙인다(이중 제출). 요청의 기존 쿠키는 유지한다. */
    public static RequestPostProcessor token(String token) {
        return request -> {
            Cookie[] existing = request.getCookies();
            Cookie added = new Cookie(COOKIE, token);
            Cookie[] all = existing == null ? new Cookie[] {added} : append(existing, added);
            request.setCookies(all);
            request.addHeader(HEADER, token);
            return request;
        };
    }

    private static Cookie[] append(Cookie[] existing, Cookie added) {
        Cookie[] all = Arrays.copyOf(existing, existing.length + 1);
        all[existing.length] = added;
        return all;
    }
}
