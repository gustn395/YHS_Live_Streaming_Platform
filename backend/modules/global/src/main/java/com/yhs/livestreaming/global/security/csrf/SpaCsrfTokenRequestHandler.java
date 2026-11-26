package com.yhs.livestreaming.global.security.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * SPA용 CSRF 토큰 처리기(G-D014). Spring Security 6.5 공식 문서 "Single-Page Applications" 구성을 따른다.
 * <ul>
 *     <li>요청 속성으로 노출하는 토큰(서버가 렌더링하는 값)은 XOR로 가려 BREACH 공격에 대응한다.</li>
 *     <li>SPA가 {@code XSRF-TOKEN} 쿠키 원문을 {@code X-XSRF-TOKEN} 헤더로 보내면 원문 그대로 비교한다.</li>
 * </ul>
 * 문서 예시는 {@link #handle}에서 지연 토큰을 매 요청 로드해 쿠키를 내보내지만, 이 프로젝트는 초기 토큰을 전용
 * {@code GET /api/v1/auth/csrf}로만 발급하므로(G-D014, BE-03 3라운드 Q4) 매 요청 로드는 하지 않는다.
 * Spring Security 7의 {@code csrf.spa()}는 쓰지 않는다(Boot 3.5 / Security 6.5 호환 API 우선).
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
