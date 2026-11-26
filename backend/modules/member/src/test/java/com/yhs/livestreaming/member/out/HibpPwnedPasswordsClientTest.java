package com.yhs.livestreaming.member.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.yhs.livestreaming.member.app.password.BreachedPasswordChecker.Result;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * MB-T012~MB-T016: HIBP range API 어댑터(MB-D010). 실제 HIBP 대신 JDK 내장 HTTP 서버 스텁을 쓴다.
 * {@code password}의 SHA-1은 {@code 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8}이다.
 */
@ExtendWith(OutputCaptureExtension.class)
class HibpPwnedPasswordsClientTest {

    private static final String PASSWORD = "password";
    private static final String PREFIX = "5BAA6";
    private static final String SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";
    private static final String OTHER_SUFFIX = "0018A45C4D1DEF81644B54AB7F969B88D65";

    private final List<Exchange> exchanges = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOnlyFiveCharPrefixWithPaddingHeader() { // MB-T012
        start(200, OTHER_SUFFIX + ":3\r\n", 0);

        assertThat(client(Duration.ofSeconds(2)).check(PASSWORD)).isEqualTo(Result.NOT_BREACHED);

        Exchange exchange = exchanges.get(0);
        assertThat(exchange.path()).isEqualTo("/range/" + PREFIX);
        assertThat(exchange.addPadding()).isEqualTo("true");
        assertThat(exchange.userAgent()).isNotBlank();
        assertThat(exchange.path() + exchange.userAgent()).doesNotContain(SUFFIX, PASSWORD);
    }

    @Test
    void positiveCountForMatchingSuffixIsBreached() { // MB-T013
        start(200, OTHER_SUFFIX + ":1\r\n" + SUFFIX + ":10437277\r\n", 0);

        assertThat(client(Duration.ofSeconds(2)).check(PASSWORD)).isEqualTo(Result.BREACHED);
    }

    @Test
    void suffixComparisonIgnoresCase() { // MB-T013
        start(200, SUFFIX.toLowerCase() + ":2\r\n", 0);

        assertThat(client(Duration.ofSeconds(2)).check(PASSWORD)).isEqualTo(Result.BREACHED);
    }

    @Test
    void paddingEntryWithZeroCountIsNotBreached() { // MB-T013
        start(200, OTHER_SUFFIX + ":5\r\n" + SUFFIX + ":0\r\n", 0);

        assertThat(client(Duration.ofSeconds(2)).check(PASSWORD)).isEqualTo(Result.NOT_BREACHED);
    }

    @Test
    void nfcAndNfdInputsQueryTheSamePrefix() { // MB-T014
        start(200, OTHER_SUFFIX + ":1\r\n", 0);
        HibpPwnedPasswordsClient client = client(Duration.ofSeconds(2));
        String nfc = Normalizer.normalize("crème brûlée forever", Normalizer.Form.NFC);
        String nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD);

        client.check(nfc);
        client.check(nfd);

        assertThat(exchanges).hasSize(2);
        assertThat(exchanges.get(0).path()).isEqualTo(exchanges.get(1).path());
    }

    @Test
    void emptyBodyIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016
        start(200, "", 0);
        assertUnavailableWithoutLeak(output);
    }

    @Test
    void blankLinesOnlyIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016
        start(200, "\r\n\r\n", 0);
        assertUnavailableWithoutLeak(output);
    }

    @Test
    void malformedLineIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016
        start(200, OTHER_SUFFIX + ":1\r\n<html>maintenance</html>\r\n", 0);
        assertUnavailableWithoutLeak(output);
    }

    @Test
    void nonNumericCountIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016
        start(200, SUFFIX + ":abc\r\n", 0);
        assertUnavailableWithoutLeak(output);
    }

    @Test
    void wrongLengthSuffixIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016
        start(200, SUFFIX.substring(1) + ":3\r\n", 0);
        assertUnavailableWithoutLeak(output);
    }

    @Test
    void redirectIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016: 리다이렉트를 따르지 않는다
        start(301, "", 0);
        assertUnavailableWithoutLeak(output);
    }

    @Test
    void invalidBaseUrlIsUnavailableWithoutLeak(CapturedOutput output) { // MB-T015, MB-T016: 요청 생성 실패도 URL을 흘리지 않는다
        HibpPwnedPasswordsClient client = new HibpPwnedPasswordsClient(new HibpProperties(
                URI.create("ftp://127.0.0.1:21"), Duration.ofMillis(500), Duration.ofSeconds(1)));

        assertThat(client.check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);
        assertNoLeak(output);
    }

    @Test
    void serverErrorIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016
        start(503, "Service Unavailable", 0);
        assertUnavailableWithoutLeak(output);
    }

    @Test
    void tooManyRequestsIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016
        start(429, "", 0);
        assertUnavailableWithoutLeak(output);
    }

    @Test
    void readTimeoutIsUnavailable(CapturedOutput output) { // MB-T015, MB-T016
        start(200, SUFFIX + ":1\r\n", 1_500);

        assertThat(client(Duration.ofMillis(300)).check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);
        assertNoLeak(output);
    }

    @Test
    void connectionFailureIsUnavailable(CapturedOutput output) throws IOException { // MB-T015, MB-T016
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        HibpPwnedPasswordsClient client = new HibpPwnedPasswordsClient(new HibpProperties(
                URI.create("http://127.0.0.1:" + closedPort), Duration.ofMillis(500), Duration.ofSeconds(1)));

        assertThat(client.check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);
        assertNoLeak(output);
    }

    private void assertUnavailableWithoutLeak(CapturedOutput output) {
        assertThat(client(Duration.ofSeconds(2)).check(PASSWORD)).isEqualTo(Result.UNAVAILABLE);
        assertNoLeak(output);
    }

    private static void assertNoLeak(CapturedOutput output) {
        assertThat(output.getAll())
                .contains("HIBP") // 장애는 원인 종류와 함께 남는다
                .doesNotContain(PASSWORD, PREFIX, PREFIX.toLowerCase(), SUFFIX, "/range/");
    }

    private HibpPwnedPasswordsClient client(Duration readTimeout) {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new HibpPwnedPasswordsClient(new HibpProperties(base, Duration.ofSeconds(1), readTimeout));
    }

    private void start(int status, String body, long delayMillis) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/", exchange -> {
            exchanges.add(new Exchange(exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Add-Padding"),
                    exchange.getRequestHeaders().getFirst("User-Agent")));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
    }

    private record Exchange(String path, String addPadding, String userAgent) {
    }
}
