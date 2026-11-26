package com.yhs.livestreaming.openapi;

import static testsupport.TestAuthentications.member;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import testsupport.AppIntegrationTest;

/**
 * GL-T009: application.yml 기본값(SPRINGDOC_ENABLED 미설정 = 운영 기본)에서는 OpenAPI 문서와 Swagger UI가 없다(G-D008).
 * 익명 요청은 공개 경로가 아니라서 401, 인증된 요청도 문서가 없어서 404다.
 * 이 테스트는 실행 환경에 SPRINGDOC_ENABLED가 설정되지 않았다는 전제로 기본값을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AppIntegrationTest
class OpenApiDisabledByDefaultIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsAreNotPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocsAndSwaggerUiDoNotExistEvenForAuthenticatedMember() throws Exception {
        mockMvc.perform(get("/v3/api-docs").with(member(42L)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html").with(member(42L)))
                .andExpect(status().isNotFound());
    }
}
