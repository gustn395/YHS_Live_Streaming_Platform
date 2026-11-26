package com.yhs.livestreaming.global.security.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * 인증 상태가 바뀔 때 CSRF 토큰을 새로 만든다(G-D014, 공개 API). 자체 로그인 API는 Spring Security 인증 성공 처리를
 * 거치지 않으므로, OTP 최종 인증 성공 직후 호출하는 쪽이 명시적으로 부른다(비밀번호 1차 인증에서는 부르지 않는다).
 *
 * <p>{@link CsrfAuthenticationStrategy}처럼 새 토큰을 만들어 저장하되, 삭제 쿠키를 먼저 보내지 않고 같은 이름·Path의 새
 * {@code XSRF-TOKEN} 쿠키 하나로 덮어쓴다(응답에 같은 쿠키가 두 번 실리지 않게).
 */
public class CsrfTokenRenewer {

    private final CsrfTokenRepository repository;

    public CsrfTokenRenewer(CsrfTokenRepository repository) {
        this.repository = repository;
    }

    public void renew(HttpServletRequest request, HttpServletResponse response) {
        repository.saveToken(repository.generateToken(request), request, response);
    }
}
