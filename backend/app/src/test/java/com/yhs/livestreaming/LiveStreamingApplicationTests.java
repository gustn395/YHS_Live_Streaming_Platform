package com.yhs.livestreaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 외부 인프라(DB, Redis) 없이 애플리케이션이 뜨고, 실제 내장 Tomcat에서 상태 확인 엔드포인트만 노출되는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveStreamingApplicationTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointReturnsUpWithoutDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        // show-details: never → 구성 요소별 상세 정보를 노출하지 않는다.
        assertThat(response.body()).doesNotContain("components");
    }

    @Test
    void otherActuatorEndpointsRequireAuthenticationForAnonymous() throws Exception {
        // 보안 체인이 먼저 동작하므로 익명 요청은 401이다(G-D011). 인증된 요청이 404(비노출)인지는
        // SecurityChainIntegrationTest에서 확인한다.
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
        assertThat(get("/actuator/beans").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
