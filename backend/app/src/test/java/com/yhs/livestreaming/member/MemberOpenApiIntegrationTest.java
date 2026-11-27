package com.yhs.livestreaming.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import testsupport.AppIntegrationTest;

/** MB-T060: 내 정보 API Spec이 실제 동작과 맞는다(MB-D028, G-D008). 실제 응답은 MemberProfileApiIntegrationTest가 확인한다. */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@AppIntegrationTest
class MemberOpenApiIntegrationTest {

    private static final String GET_ME = "$.paths['/api/v1/members/me'].get";
    private static final String PATCH_ME = "$.paths['/api/v1/members/me'].patch";
    private static final String PROBLEM = ".content['application/problem+json'].schema['$ref']";
    private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void myProfileSpecsDocumentActualResponses() throws Exception { // MB-T060
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(GET_ME + ".tags[0]").value("Member"))
                .andExpect(jsonPath(GET_ME + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(GET_ME + ".responses['200']").exists())
                .andExpect(jsonPath(GET_ME + ".responses['401']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(GET_ME + ".responses['403']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(GET_ME + ".responses['503']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(GET_ME + ".parameters").doesNotExist())
                .andExpect(jsonPath(PATCH_ME + ".tags[0]").value("Member"))
                // 쿠키와 CSRF가 모두 필요하다: 요구사항 객체 하나(MemberOpenApiConfig, module.md 내 정보 절)
                .andExpect(jsonPath(PATCH_ME + ".security.length()").value(1))
                .andExpect(jsonPath(PATCH_ME + ".security[0].accessTokenCookie").exists())
                .andExpect(jsonPath(PATCH_ME + ".security[0].csrfHeader").exists())
                .andExpect(jsonPath(GET_ME + ".security.length()").value(1))
                .andExpect(jsonPath(GET_ME + ".security[0].csrfHeader").doesNotExist())
                .andExpect(jsonPath(PATCH_ME + ".responses['200']").exists())
                .andExpect(jsonPath(PATCH_ME + ".responses['400']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(PATCH_ME + ".responses['401']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(PATCH_ME + ".responses['403']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(PATCH_ME + ".responses['503']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(PATCH_ME + ".parameters").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MemberProfileResponse.properties.memberId").exists())
                .andExpect(jsonPath("$.components.schemas.MemberProfileResponse.properties.username").exists())
                .andExpect(jsonPath("$.components.schemas.MemberProfileResponse.properties.accountStatus").exists())
                .andExpect(jsonPath("$.components.schemas.MemberProfileResponse.properties.passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.NicknameUpdateRequest.properties.nickname").exists())
                .andExpect(jsonPath("$.components.schemas.NicknameUpdateRequest.properties.username")
                        .doesNotExist());
    }
}
