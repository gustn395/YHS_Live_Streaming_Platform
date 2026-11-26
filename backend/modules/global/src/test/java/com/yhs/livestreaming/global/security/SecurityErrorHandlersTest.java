package com.yhs.livestreaming.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * GL-T001·GL-T002: Security 필터 단계의 401·403은 MVC 예외 처리기(GlobalExceptionHandler)로 넘겨 같은 ProblemDetail
 * 형식으로 응답한다(G-D003, R-22). 처리기에 받은 예외를 그대로 넘기는지가 이 클래스의 계약이다.
 */
class SecurityErrorHandlersTest {

    private final RecordingResolver resolver = new RecordingResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/protected");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void entryPointForwardsAuthenticationExceptionToResolver() throws Exception {
        InsufficientAuthenticationException exception = new InsufficientAuthenticationException("anonymous");

        new ProblemDetailAuthenticationEntryPoint(resolver).commence(request, response, exception);

        assertThat(resolver.received).isSameAs(exception);
        assertThat(resolver.receivedRequest).isSameAs(request);
        assertThat(resolver.receivedResponse).isSameAs(response);
    }

    @Test
    void accessDeniedHandlerForwardsAccessDeniedExceptionToResolver() throws Exception {
        AccessDeniedException exception = new AccessDeniedException("denied");

        new ProblemDetailAccessDeniedHandler(resolver).handle(request, response, exception);

        assertThat(resolver.received).isSameAs(exception);
        assertThat(resolver.receivedRequest).isSameAs(request);
        assertThat(resolver.receivedResponse).isSameAs(response);
    }

    private static final class RecordingResolver implements HandlerExceptionResolver {
        private Exception received;
        private HttpServletRequest receivedRequest;
        private HttpServletResponse receivedResponse;

        @Override
        public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
                                             Exception ex) {
            this.received = ex;
            this.receivedRequest = request;
            this.receivedResponse = response;
            return new ModelAndView();
        }
    }
}
