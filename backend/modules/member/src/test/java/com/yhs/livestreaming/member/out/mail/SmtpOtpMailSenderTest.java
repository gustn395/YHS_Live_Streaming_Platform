package com.yhs.livestreaming.member.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;

/**
 * MB-T026(SMTP): 실제 SMTP 발송은 Mailpit(Testcontainers, 인증·TLS 없음)으로, 연결 실패·응답 없는 서버(타임아웃)는 로컬 소켓으로
 * 재현한다. 실패는 {@link OtpDeliveryException}이고 메일 주소·OTP·SMTP 오류 원문이 예외·로그에 없다(MB-D016). 실제 Gmail은
 * 부르지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class SmtpOtpMailSenderTest {

    private static final String TO = "tester@example.com";
    private static final Instant EXPIRES = Instant.parse("2026-11-26T10:05:00Z");

    static final GenericContainer<?> mailpit =
            new GenericContainer<>("axllent/mailpit:v1.21.0").withExposedPorts(1025, 8025);

    static {
        mailpit.start();
    }

    @Test
    void sendsTheCodeThroughSmtp() throws Exception {
        SmtpOtpMailSender sender = sender(mailpit.getHost(), mailpit.getMappedPort(1025), 3_000);

        sender.send(TO, "0420", EXPIRES);

        String messages = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                        "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/messages")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertThat(messages).contains(TO).contains("no-reply@example.com");
        String id = messages.replaceAll("(?s).*\"ID\":\"([^\"]+)\".*", "$1");
        String body = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                        "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + "/api/v1/message/" + id))
                .build(), HttpResponse.BodyHandlers.ofString()).body();
        assertThat(body).contains("0420");
    }

    @Test
    void refusedConnectionIsADeliveryFailureWithoutDetails(CapturedOutput output) {
        SmtpOtpMailSender sender = sender("127.0.0.1", 1, 1_000);

        assertThatThrownBy(() -> sender.send(TO, "0420", EXPIRES))
                .isInstanceOf(OtpDeliveryException.class)
                .hasNoCause()
                .satisfies(ex -> assertThat(String.valueOf(ex.getMessage())).doesNotContain(TO).doesNotContain("0420"));
        assertThat(output).doesNotContain(TO).doesNotContain("0420");
    }

    @Test
    void silentServerTimesOutWithinTheConfiguredLimit() throws Exception {
        CopyOnWriteArrayList<Socket> held = new CopyOnWriteArrayList<>();
        try (ServerSocket silent = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try {
                    held.add(silent.accept()); // 연결만 받고 SMTP 인사말을 보내지 않는다
                } catch (IOException ignored) {
                    // 테스트 종료
                }
            });
            acceptor.start();
            SmtpOtpMailSender sender = sender("127.0.0.1", silent.getLocalPort(), 500);

            long started = System.nanoTime();
            assertThatThrownBy(() -> sender.send(TO, "0420", EXPIRES)).isInstanceOf(OtpDeliveryException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        } finally {
            for (Socket socket : held) {
                socket.close();
            }
        }
    }

    private static SmtpOtpMailSender sender(String host, int port, int timeoutMillis) {
        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(host);
        mail.setPort(port);
        Properties properties = new Properties();
        properties.put("mail.smtp.connectiontimeout", Integer.toString(timeoutMillis));
        properties.put("mail.smtp.timeout", Integer.toString(timeoutMillis));
        properties.put("mail.smtp.writetimeout", Integer.toString(timeoutMillis));
        mail.setJavaMailProperties(properties);
        return new SmtpOtpMailSender(mail, new MemberOtpMailProperties("no-reply@example.com"));
    }
}
