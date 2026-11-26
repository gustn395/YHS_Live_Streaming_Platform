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

/**
 * MB-T011: 회원가입 API의 Swagger Spec(ApiV1AuthSpec)이 실제 동작(201·400·409·503, 요청 필드, 오류 스키마)과 맞는다(G-D008).
 * 실제 응답 코드는 SignupApiIntegrationTest가 확인한다.
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@AppIntegrationTest
class SignupOpenApiIntegrationTest {

    private static final String SIGNUP = "$.paths['/api/v1/auth/signup'].post";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signupSpecDocumentsActualResponsesAndRequestFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SIGNUP + ".tags[0]").value("Authentication"))
                .andExpect(jsonPath(SIGNUP + ".responses['201']").exists())
                .andExpect(jsonPath(SIGNUP + ".responses['400'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath(SIGNUP + ".responses['409'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath(SIGNUP + ".responses['503'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.username").exists())
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.passwordConfirm").exists())
                .andExpect(jsonPath("$.components.schemas.SignupResponse.properties.memberId").exists())
                .andExpect(jsonPath("$.components.schemas.SignupResponse.properties.accountStatus").exists());
    }
}
