package com.yhs.livestreaming.global.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GL-T004·GL-T005·GL-T010: 오류는 ProblemDetail + code(+ errors[])로 응답하고, 내부 메시지는 노출하지 않는다(G-D003).
 * 외부 인프라 없이 standalone MockMvc로 실행한다(G-D009).
 */
class GlobalExceptionHandlerTest {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validationErrorReturnsBadRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void malformedJsonReturnsBadRequestWithCode() throws Exception {
        mockMvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"));
    }

    @Test
    void unexpectedExceptionReturnsInternalErrorWithoutInternalMessage() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("db-password"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void businessExceptionUsesItsErrorCode() throws Exception {
        mockMvc.perform(get("/business"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TEST_DUPLICATE"))
                .andExpect(jsonPath("$.detail").value("이미 있는 값입니다."));
    }

    @Test
    void sessionStoreFailureReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/session-store"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_STORE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("redis-host"))));
    }

    @Test
    void authenticationExceptionReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void accessDeniedForAnonymousReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    @Test
    void accessDeniedForAuthenticatedMemberReturnsForbidden() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(42L, "USER", "session-1"), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    void requestParameterConstraintViolationReturnsBadRequestWithFieldErrors() throws Exception { // GL-T004
        mockMvc.perform(get("/param").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void methodParameterConstraintViolationReturnsBadRequest() throws Exception { // GL-T004: 입력값 검증 실패
        mockMvc.perform(get("/parameter-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void returnValueConstraintViolationIsServerError() throws Exception { // GL-T005: 서버가 만든 값의 검증 실패
        mockMvc.perform(get("/return-value-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void controllerReturnValueConstraintViolationIsServerError() throws Exception { // GL-T005: MVC 반환값 검증 실패
        mockMvc.perform(get("/return-blank"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(not(containsString("returnBlank"))));
    }

    @Test
    void beanConstraintViolationOutsideMethodParametersIsServerError() throws Exception { // GL-T005: 저장 직전 검증 등
        mockMvc.perform(get("/bean-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"));
    }

    @Test
    void errorIsProblemJsonEvenWhenClientAcceptsOnlyHtml() throws Exception { // GL-T005
        mockMvc.perform(get("/boom").accept(MediaType.TEXT_HTML))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"));
        mockMvc.perform(get("/no-such-path").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    @Test
    void unknownPathReturnsNotFoundWithCode() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    record EchoRequest(@NotBlank String name) {
    }

    enum TestErrorCode implements ErrorCode {
        DUPLICATE;

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String code() {
            return "TEST_DUPLICATE";
        }
    }

    @RestController
    static class FailingController {

        @PostMapping("/echo")
        String echo(@Valid @RequestBody EchoRequest request) {
            return request.name();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("db-password=secret");
        }

        @GetMapping("/business")
        String business() {
            throw new BusinessException(TestErrorCode.DUPLICATE, "이미 있는 값입니다.");
        }

        @GetMapping("/session-store")
        String sessionStore() {
            throw new LoginSessionStoreUnavailableException("redis-host:6379 timeout", new RuntimeException());
        }

        @GetMapping("/unauthenticated")
        String unauthenticated() {
            throw new AuthenticationCredentialsNotFoundException("no authentication");
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/param")
        String param(@RequestParam @Min(1) int size) {
            return String.valueOf(size);
        }

        @GetMapping("/return-blank")
        @NotBlank
        String returnBlank() {
            return " ";
        }

        @GetMapping("/parameter-violation")
        String parameterViolation() throws NoSuchMethodException {
            // @Validated 서비스가 사용자 입력 인자를 검증하다 실패한 상황(MethodValidationInterceptor가 던지는 예외와 같다)
            Method save = ValidatedService.class.getDeclaredMethod("save", String.class);
            Set<ConstraintViolation<ValidatedService>> violations =
                    VALIDATOR.forExecutables().validateParameters(new ValidatedService(), save, new Object[] {" "});
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/return-value-violation")
        String returnValueViolation() throws NoSuchMethodException {
            // 서버가 만든 반환값이 제약을 어긴 상황: 클라이언트 입력 문제가 아니다
            Method findName = ValidatedService.class.getDeclaredMethod("findName");
            Set<ConstraintViolation<ValidatedService>> violations =
                    VALIDATOR.forExecutables().validateReturnValue(new ValidatedService(), findName, " ");
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/bean-violation")
        String beanViolation() {
            // 엔티티 저장 직전 검증처럼 메서드 인자가 아닌 객체 검증이 실패한 상황
            throw new ConstraintViolationException(VALIDATOR.validate(new EchoRequest(" ")));
        }
    }

    static class ValidatedService {
        String save(@NotBlank String name) {
            return name;
        }

        @NotBlank
        String findName() {
            return " ";
        }
    }

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
}
