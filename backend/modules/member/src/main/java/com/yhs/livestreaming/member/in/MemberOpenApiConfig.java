package com.yhs.livestreaming.member.in;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 회원 정보 API의 OpenAPI 보안 요구사항 보정(MB-D028, G-D008). OpenAPI에서 {@code security} 배열의 원소끼리는 "또는",
 * 한 원소 안의 스킴끼리는 "그리고"다. Swagger 애너테이션의 {@code @SecurityRequirement}를 여러 개 적으면 원소가 여러 개
 * ("쿠키 또는 CSRF")가 되므로, {@link ApiV1MemberController}의 요구사항을 실제 동작대로 원소 하나("쿠키 그리고 CSRF")로 합친다.
 */
@Configuration(proxyBeanMethods = false)
class MemberOpenApiConfig {

    @Bean
    OperationCustomizer memberSecurityRequirementCustomizer() {
        return (operation, handlerMethod) -> {
            List<SecurityRequirement> requirements = operation.getSecurity();
            if (handlerMethod.getBeanType() != ApiV1MemberController.class || requirements == null
                    || requirements.size() < 2) {
                return operation;
            }
            SecurityRequirement all = new SecurityRequirement();
            requirements.forEach(requirement -> requirement.forEach(all::addList));
            operation.setSecurity(List.of(all));
            return operation;
        };
    }
}
