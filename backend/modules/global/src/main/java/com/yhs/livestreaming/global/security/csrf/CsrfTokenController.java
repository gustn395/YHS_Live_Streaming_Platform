package com.yhs.livestreaming.global.security.csrf;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 전 CSRF 토큰 초기 발급(G-D014). 지연 로딩된 토큰을 실제로 로드해 {@code XSRF-TOKEN} 쿠키를 내보낸다.
 * 응답 본문은 없고 캐시하지 않는다.
 */
@RestController
@Tag(name = "Authentication")
public class CsrfTokenController {

    @Operation(summary = "CSRF 토큰 발급",
            description = "XSRF-TOKEN 쿠키를 발급한다. 상태를 바꾸는 요청(로그인·가입·OTP 포함)은 이 쿠키 값을 "
                    + "X-XSRF-TOKEN 헤더로 보내야 한다.")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "XSRF-TOKEN 쿠키 발급, 본문 없음")
    @GetMapping(CsrfConfig.CSRF_ENDPOINT)
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken(); // 지연 토큰을 로드해야 저장소가 쿠키를 쓴다
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
