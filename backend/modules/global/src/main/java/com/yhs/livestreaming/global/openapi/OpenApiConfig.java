package com.yhs.livestreaming.global.openapi;

import com.yhs.livestreaming.global.security.PublicEndpoint;
import com.yhs.livestreaming.global.security.PublicEndpointContributor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/**
 * OpenAPI 공통 설정(G-D008, G-D012). 각 API의 Spec 인터페이스는 그 API를 구현할 때 작성한다.
 * 문서 노출은 {@code springdoc.api-docs.enabled}가 켜졌을 때만이고, 운영 기본값은 꺼짐이다(application.yml).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /** 공통 오류 응답 스키마 이름. 각 API는 401·403·409 등 오류 응답에서 이 스키마를 참조한다. */
    public static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";

    @Bean
    OpenAPI yhsOpenApi() {
        return new OpenAPI()
                .info(new Info().title("YHS Live Streaming API").version("v1"))
                .components(new Components());
    }

    /**
     * 공통 오류 스키마를 문서 생성이 끝난 뒤에 넣는다. springdoc은 예외 처리기의 반환형(Spring {@code ProblemDetail})으로
     * 같은 이름의 스키마를 자동 생성하는데, 그 스키마에는 확장 필드 code·errors가 없어서 덮어써야 한다.
     */
    @Bean
    OpenApiCustomizer problemDetailSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema());
        };
    }

    /** springdoc이 켜졌을 때만 문서 경로를 공개 경로로 등록한다. 꺼져 있으면 보호 경로로 남는다(G-D004). */
    @Bean
    @ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
    PublicEndpointContributor openApiPublicEndpoints() {
        return () -> List.of(
                PublicEndpoint.of(HttpMethod.GET, "/v3/api-docs"),
                PublicEndpoint.of(HttpMethod.GET, "/v3/api-docs/**"),
                PublicEndpoint.of(HttpMethod.GET, "/swagger-ui.html"),
                PublicEndpoint.of(HttpMethod.GET, "/swagger-ui/**"));
    }

    @SuppressWarnings("rawtypes")
    private static Schema problemDetailSchema() {
        ObjectSchema fieldError = new ObjectSchema();
        fieldError.addProperty("field", new StringSchema().description("값이 잘못된 요청 필드"));
        fieldError.addProperty("message", new StringSchema().description("검증 실패 이유"));

        ObjectSchema schema = new ObjectSchema();
        schema.description("RFC 9457 ProblemDetail + 확장 필드 code·errors (docs/policy.md G-D003)");
        schema.addProperty("type", new StringSchema().description("문제 유형 URI"));
        schema.addProperty("title", new StringSchema().description("HTTP 상태 요약"));
        schema.addProperty("status", new IntegerSchema().description("HTTP 상태 코드"));
        schema.addProperty("detail", new StringSchema().description("사용자에게 보여 줄 설명"));
        schema.addProperty("instance", new StringSchema().description("요청 경로"));
        schema.addProperty("code", new StringSchema().description("기계용 오류 코드(예: AUTH_UNAUTHENTICATED)"));
        schema.addProperty("errors", new ArraySchema().items(fieldError).description("요청 검증 오류(400일 때)"));
        return schema;
    }
}
