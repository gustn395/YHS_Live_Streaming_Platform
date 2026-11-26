package com.yhs.livestreaming.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GL-T008: 개발·테스트처럼 springdoc을 켠 환경에서는 OpenAPI 문서와 Swagger UI를 인증 없이 볼 수 있고, 문서에 공통 오류
 * 스키마(ProblemDetail + code + errors)가 들어 있다(G-D008).
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
class OpenApiEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsArePublicAndContainCommonErrorSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.errors").exists())
                .andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.status").exists());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
