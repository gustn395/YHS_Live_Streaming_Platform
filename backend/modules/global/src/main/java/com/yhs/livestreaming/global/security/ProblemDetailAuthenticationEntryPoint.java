package com.yhs.livestreaming.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 필터 단계의 인증 실패(401)를 MVC 예외 처리기로 넘겨 ProblemDetail로 응답한다(G-D003, R-22).
 * 응답 본문을 직접 쓰지 않고 MVC의 메시지 변환기를 그대로 쓰므로 JSON 형식이 컨트롤러 오류와 같다.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    public ProblemDetailAuthenticationEntryPoint(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) {
        resolver.resolveException(request, response, null, authException);
    }
}
