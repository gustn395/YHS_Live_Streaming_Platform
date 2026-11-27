package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.security.PublicEndpoint;
import com.yhs.livestreaming.global.security.PublicEndpointContributor;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * member가 인증 없이 여는 경로(G-D004). 방문자가 호출하는 가입·로그인·OTP와, Refresh 쿠키로 인증하는 Refresh·로그아웃이다. 공개 경로여도 CSRF 토큰은 필요하다(G-D014).
 */
@Component
public class MemberPublicEndpoints implements PublicEndpointContributor {

    @Override
    public Collection<PublicEndpoint> publicEndpoints() {
        return List.of(
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/signup"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/login"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/otp/verify"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/otp/resend"),
                // Refresh·로그아웃은 Access가 없거나 만료돼도 호출한다(MB-D020, MB-D022)
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/refresh"),
                PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/logout"));
    }
}
