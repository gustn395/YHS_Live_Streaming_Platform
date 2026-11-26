package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.security.PublicEndpoint;
import com.yhs.livestreaming.global.security.PublicEndpointContributor;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/** member가 인증 없이 여는 경로(G-D004). 회원가입은 방문자가 호출한다. */
@Component
public class MemberPublicEndpoints implements PublicEndpointContributor {

    @Override
    public Collection<PublicEndpoint> publicEndpoints() {
        return List.of(PublicEndpoint.of(HttpMethod.POST, "/api/v1/auth/signup"));
    }
}
