package com.yhs.livestreaming.global.security;

import java.util.Collection;

/**
 * 모듈이 공개 경로를 등록하는 확장점(G-D004). 각 모듈은 이 인터페이스를 Bean으로 등록하고, global 보안 체인이 모두 모아
 * {@code permitAll}로 둔다. 여기에 없는 경로는 인증이 필요하다. global은 모듈의 경로를 직접 알지 않는다.
 */
@FunctionalInterface
public interface PublicEndpointContributor {

    Collection<PublicEndpoint> publicEndpoints();
}
