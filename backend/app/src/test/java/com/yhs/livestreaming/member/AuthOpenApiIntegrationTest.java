package com.yhs.livestreaming.member;

import static org.hamcrest.Matchers.hasItem;
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
 * GL-T015·MB-T032: OpenAPI 보안 스킴(Access 쿠키, CSRF 헤더)과 로그인·OTP·CSRF API Spec이 실제 동작과 맞는다(G-D008, MB-D013,
 * G-D014). 실제 응답은 LoginApiIntegrationTest·CsrfIntegrationTest가 확인한다.
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@AppIntegrationTest
class AuthOpenApiIntegrationTest {

    private static final String LOGIN = "$.paths['/api/v1/auth/login'].post";
    private static final String VERIFY = "$.paths['/api/v1/auth/otp/verify'].post";
    private static final String RESEND = "$.paths['/api/v1/auth/otp/resend'].post";
    private static final String PROBLEM = ".content['application/problem+json'].schema['$ref']";
    private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void securitySchemesDescribeAccessCookieAndCsrfHeader() throws Exception { // GL-T015
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.accessTokenCookie.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.accessTokenCookie.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.accessTokenCookie.name").value("__Host-yhs_at"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.name").value("X-XSRF-TOKEN"));
    }

    @Test
    void loginAndOtpSpecsDocumentActualResponses() throws Exception { // MB-T032
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LOGIN + ".tags[0]").value("Authentication"))
                .andExpect(jsonPath(LOGIN + ".security[0].csrfHeader").exists())
                .andExpect(jsonPath(LOGIN + ".responses['200']").exists())
                .andExpect(jsonPath(LOGIN + ".responses['401']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(LOGIN + ".responses['403']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(LOGIN + ".responses['429']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(LOGIN + ".responses['429'].headers['Retry-After']").exists())
                .andExpect(jsonPath(LOGIN + ".responses['503']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(LOGIN + ".parameters").doesNotExist()) // HttpServletRequest·Response는 숨김
                .andExpect(jsonPath(VERIFY + ".responses['200']").exists())
                .andExpect(jsonPath(VERIFY + ".responses['400']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(VERIFY + ".responses['401']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(VERIFY + ".responses['403']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(VERIFY + ".responses['429']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(VERIFY + ".responses['503']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(VERIFY + ".parameters[?(@.in == 'cookie')].name", hasItem("__Host-yhs_otp")))
                .andExpect(jsonPath(RESEND + ".responses['200']").exists())
                .andExpect(jsonPath(RESEND + ".responses['401']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(RESEND + ".responses['429']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath("$.paths['/api/v1/auth/csrf'].get.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.username").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password").exists())
                .andExpect(jsonPath("$.components.schemas.OtpVerifyRequest.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.OtpRequiredResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.OtpRequiredResponse.properties.otpExpiresAt").exists())
                .andExpect(jsonPath("$.components.schemas.LoginSuccessResponse.properties.memberId").exists())
                .andExpect(jsonPath("$.components.schemas.LoginSuccessResponse.properties.accessTokenExpiresAt")
                        .exists());
    }

    @Test
    void refreshAndLogoutSpecsDocumentActualResponses() throws Exception { // MB-T043
        String refresh = "$.paths['/api/v1/auth/refresh'].post";
        String logout = "$.paths['/api/v1/auth/logout'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(refresh + ".tags[0]").value("Authentication"))
                .andExpect(jsonPath(refresh + ".security[0].csrfHeader").exists())
                .andExpect(jsonPath(refresh + ".responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/LoginSuccessResponse"))
                .andExpect(jsonPath(refresh + ".responses['401']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(refresh + ".responses['403']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(refresh + ".responses['429']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(refresh + ".responses['429'].headers['Retry-After']").exists())
                .andExpect(jsonPath(refresh + ".responses['500']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(refresh + ".responses['503']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(refresh + ".parameters[?(@.in == 'cookie')].name", hasItem("__Secure-yhs_rt")))
                .andExpect(jsonPath(logout + ".tags[0]").value("Authentication"))
                .andExpect(jsonPath(logout + ".security[0].csrfHeader").exists())
                .andExpect(jsonPath(logout + ".responses['204']").exists())
                .andExpect(jsonPath(logout + ".responses['204'].content").doesNotExist())
                .andExpect(jsonPath(logout + ".responses['403']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(logout + ".responses['503']" + PROBLEM).value(PROBLEM_REF))
                .andExpect(jsonPath(logout + ".responses['200']").doesNotExist())
                .andExpect(jsonPath(logout + ".parameters[?(@.in == 'cookie')].name", hasItem("__Secure-yhs_rt")));
    }
}
