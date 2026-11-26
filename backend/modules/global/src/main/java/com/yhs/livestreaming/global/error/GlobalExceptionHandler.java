package com.yhs.livestreaming.global.error;

import com.yhs.livestreaming.global.auth.LoginSessionStoreUnavailableException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류를 ProblemDetail(RFC 9457) + {@code code}(+ {@code errors[]})로 응답한다(G-D003).
 *
 * <p>Spring Security 필터 단계의 401·403도 {@code ProblemDetailAuthenticationEntryPoint}·
 * {@code ProblemDetailAccessDeniedHandler}가 이 처리기로 넘기므로 형식이 같다(R-22). 내부 예외 메시지는 응답에 쓰지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CODE = "code";
    private static final String AUTHENTICATION_REQUIRED = "인증이 필요합니다.";
    private static final String INTERNAL_ERROR_DETAIL = "서버 오류가 발생했습니다.";

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        if (ex instanceof TooManyRequestsException tooMany) {
            return ResponseEntity.status(ex.errorCode().status())
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(tooMany.retryAfterSeconds()))
                    .body(problemBody(ex.errorCode(), ex.getMessage()));
        }
        return problem(ex.errorCode(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return problem(CommonErrorCode.UNAUTHENTICATED, AUTHENTICATION_REQUIRED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        // CSRF 토큰 누락·불일치는 인증 여부와 관계없이 403 AUTH_CSRF_INVALID다(G-D014). FE는 토큰을 다시 받아 재시도한다.
        if (ex instanceof CsrfException) {
            return problem(CommonErrorCode.CSRF_INVALID, "요청을 확인할 수 없습니다. 페이지를 새로 고친 뒤 다시 시도해 주세요.");
        }
        // 인증되지 않은 사용자의 접근 거부는 403이 아니라 401이다(ExceptionTranslationFilter와 같은 판단).
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || trustResolver.isAnonymous(authentication)) {
            return problem(CommonErrorCode.UNAUTHENTICATED, AUTHENTICATION_REQUIRED);
        }
        return problem(CommonErrorCode.ACCESS_DENIED, "접근 권한이 없습니다.");
    }

    @ExceptionHandler(LoginSessionStoreUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleSessionStoreUnavailable(LoginSessionStoreUnavailableException ex) {
        log.warn("로그인 세션 저장소를 사용할 수 없다", ex);
        return problem(CommonErrorCode.SESSION_STORE_UNAVAILABLE, "인증 서비스를 일시적으로 사용할 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("처리하지 않은 예외", ex);
        return problem(CommonErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL);
    }

    /** {@code @Valid @RequestBody} 검증 실패. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex, invalidInput(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 컨트롤러 메서드 검증 실패(Spring 6.1+). {@code @RequestParam}·{@code @PathVariable} 등 입력값 위반은 400이다.
     * 컨트롤러 반환값 위반({@code isForReturnValue()})은 서버가 만든 값의 문제라 500으로 처리한다(GL-T005).
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        if (ex.isForReturnValue()) {
            log.error("컨트롤러 반환값 검증 실패", ex);
            ErrorCode internalError = CommonErrorCode.INTERNAL_ERROR;
            return handleExceptionInternal(ex, problemBody(internalError, INTERNAL_ERROR_DETAIL), headers,
                    internalError.status(), request);
        }
        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(error -> errors.add(fieldError(error.getField(), error.getDefaultMessage())));
            } else {
                String parameterName = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(error -> errors.add(fieldError(parameterName, error.getDefaultMessage())));
            }
        }
        return handleExceptionInternal(ex, invalidInput(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * {@code @Validated} 빈의 메서드 검증 실패. 메서드 인자(입력값) 위반만 400이다. 반환값 위반이나 엔티티 저장 직전 같은
     * 객체 검증 위반은 서버가 만든 값의 문제라 500으로 처리한다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        boolean inputViolationOnly = violations != null && !violations.isEmpty()
                && violations.stream().allMatch(violation -> isParameterPath(violation.getPropertyPath()));
        if (!inputViolationOnly) {
            return handleUnexpected(ex);
        }
        List<Map<String, String>> errors = violations.stream()
                .map(violation -> fieldError(lastNodeName(violation.getPropertyPath()), violation.getMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(invalidInput(errors));
    }

    private static boolean isParameterPath(Path path) {
        for (Path.Node node : path) {
            if (node.getKind() == ElementKind.PARAMETER || node.getKind() == ElementKind.CROSS_PARAMETER) {
                return true;
            }
        }
        return false;
    }

    private static ProblemDetail invalidInput(List<Map<String, String>> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.");
        body.setProperty(CODE, CommonErrorCode.INVALID_INPUT.code());
        body.setProperty("errors", errors);
        return body;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field == null ? "" : field, "message", message == null ? "" : message);
    }

    /** {@code save.arg0.name} 같은 경로에서 마지막 이름({@code name})만 쓴다. */
    private static String lastNodeName(Path path) {
        String name = "";
        for (Path.Node node : path) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name;
    }

    /** 프레임워크가 만든 ProblemDetail(400·404·405 등)에도 code를 붙인다. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problemDetail && !hasCode(problemDetail)) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problemDetail.setProperty(CODE, "COMMON_" + (status != null ? status.name() : statusCode.value()));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static boolean hasCode(ProblemDetail problemDetail) {
        Map<String, Object> properties = problemDetail.getProperties();
        return properties != null && properties.containsKey(CODE);
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode errorCode, String detail) {
        return ResponseEntity.status(errorCode.status()).body(problemBody(errorCode, detail));
    }

    private static ProblemDetail problemBody(ErrorCode errorCode, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        body.setProperty(CODE, errorCode.code());
        return body;
    }
}
