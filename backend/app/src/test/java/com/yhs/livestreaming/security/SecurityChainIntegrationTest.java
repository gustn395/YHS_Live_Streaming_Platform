package com.yhs.livestreaming.security;

import static testsupport.TestAuthentications.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import testsupport.AppIntegrationTest;
import testsupport.SecurityTestEndpoints;

/**
 * 실제 보안 체인(global SecurityFilterChain)을 통과하는 통합 테스트. 외부 인프라 없이 실행한다.
 * 정책: docs/policy.md G-D003~G-D006, G-D011. 테스트 ID는 docs/global/test-spec.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
@Import(SecurityTestEndpoints.class)
class SecurityChainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void anonymousRequestToProtectedPathReturnsProblemDetail401() throws Exception { // GL-T001
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void anonymousRequestAcceptingOnlyHtmlStillGetsProblemDetail401() throws Exception { // GL-T001
        mockMvc.perform(get("/test/protected").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void authenticatedMemberWithoutPermissionReturnsProblemDetail403() throws Exception { // GL-T002
        mockMvc.perform(get("/test/protected/forbidden").with(member(42L)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    void anonymousAccessDeniedOnPublicPathReturns401() throws Exception { // GL-T002
        mockMvc.perform(get("/test/public/denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void healthIsPublic() throws Exception { // GL-T003
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void unexposedActuatorEndpointsAreNotFoundForAuthenticatedMember() throws Exception { // GL-T003, G-D011
        mockMvc.perform(get("/actuator/env").with(member(42L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
        mockMvc.perform(get("/actuator/beans").with(member(42L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unexposedActuatorEndpointsRequireAuthenticationForAnonymous() throws Exception { // GL-T003, G-D011
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void neitherAnonymousNorAuthenticatedRequestCreatesSession() throws Exception { // GL-T006
        MvcResult anonymous = mockMvc.perform(get("/test/protected"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        MvcResult authenticated = mockMvc.perform(get("/test/protected/me").with(member(42L)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(anonymous.getRequest().getSession(false)).isNull();
        assertThat(authenticated.getRequest().getSession(false)).isNull();
    }

    @Test
    void publicPostWithoutCsrfTokenIsNotRejected() throws Exception { // GL-T006, G-D005: CSRF 비활성(쿠키 인증 도입 전 한정)
        mockMvc.perform(post("/test/public/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello"));
    }

    @Test
    void currentMemberIsInjectedForAuthenticatedRequest() throws Exception { // GL-T007
        mockMvc.perform(get("/test/protected/me").with(member(42L)))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void noDefaultUserAccountIsCreated() { // GL-T006: Boot 기본 계정(생성 비밀번호 로그)을 만들지 않는다
        assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void currentMemberWithoutAuthenticationReturns401() throws Exception { // GL-T007
        mockMvc.perform(get("/test/public/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }
}
