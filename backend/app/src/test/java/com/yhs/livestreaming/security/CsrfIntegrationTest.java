package com.yhs.livestreaming.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static testsupport.TestAuthentications.member;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import testsupport.AppIntegrationTest;
import testsupport.SecurityTestEndpoints;

/**
 * GL-T014: CSRF SPA 이중 제출(G-D014). 실제 초기 발급 API({@code GET /api/v1/auth/csrf})가 내려준 쿠키와 헤더로 정상 요청을
 * 보내고, 토큰 누락·위조 요청이 공개·보호 경로 모두에서 403 {@code AUTH_CSRF_INVALID}인지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
@Import(SecurityTestEndpoints.class)
class CsrfIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void csrfEndpointIssuesTokenCookieWithoutBody() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""))
                .andReturn().getResponse();

        // CookieCsrfTokenRepository는 Servlet Cookie 속성으로 SameSite를 넘긴다. MockMvc의 Set-Cookie 문자열은 이 속성을
        // 쓰지 않으므로, 서블릿 컨테이너가 받는 Cookie 객체의 속성으로 확인한다.
        Cookie cookie = response.getCookie(CSRF_COOKIE);
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    void publicPostWithTokenFromCsrfEndpointSucceeds() throws Exception {
        String token = issueToken();

        mockMvc.perform(withToken(echo("/test/public/echo"), token, token))
                .andExpect(status().isOk())
                .andExpect(content().string("hello"));
    }

    @Test
    void protectedPostWithTokenFromCsrfEndpointSucceeds() throws Exception {
        String token = issueToken();

        mockMvc.perform(withToken(echo("/test/protected/echo"), token, token).with(member(42L)))
                .andExpect(status().isOk());
    }

    @Test
    void publicPostWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(echo("/test/public/echo"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
    }

    @Test
    void protectedPostWithoutTokenIsRejectedEvenWhenAuthenticated() throws Exception {
        mockMvc.perform(echo("/test/protected/echo").with(member(42L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
    }

    @Test
    void cookieWithoutHeaderIsRejected() throws Exception {
        String token = issueToken();

        mockMvc.perform(echo("/test/public/echo").cookie(new Cookie(CSRF_COOKIE, token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
    }

    @Test
    void forgedHeaderNotMatchingCookieIsRejected() throws Exception {
        String token = issueToken();
        String forged = issueToken(); // 다른 발급분: 공격자가 자기 쿠키에서 얻은 값

        mockMvc.perform(withToken(echo("/test/public/echo"), token, forged))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
    }

    @Test
    void headerWithoutCookieIsRejected() throws Exception {
        String token = issueToken();

        mockMvc.perform(echo("/test/public/echo").header(CSRF_HEADER, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
    }

    @Test
    void safeMethodsDoNotNeedToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private String issueToken() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        return response.getCookie(CSRF_COOKIE).getValue();
    }

    private static MockHttpServletRequestBuilder echo(String path) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"hello\"}");
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String cookie,
                                                           String header) {
        return request.cookie(new Cookie(CSRF_COOKIE, cookie)).header(CSRF_HEADER, header);
    }
}
