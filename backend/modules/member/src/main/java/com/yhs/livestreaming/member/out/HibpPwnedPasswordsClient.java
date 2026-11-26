package com.yhs.livestreaming.member.out;

import com.yhs.livestreaming.member.app.password.BreachedPasswordChecker;
import com.yhs.livestreaming.member.app.password.PasswordNormalization;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HIBP Pwned Passwords range API 어댑터(MB-D010, k-익명성).
 *
 * <p>비밀번호를 NFC → UTF-8 → SHA-1로 바꿔 앞 5자만 {@code GET /range/{prefix}}로 보내고({@code Add-Padding: true}),
 * 받은 {@code 접미사:건수} 목록에서 나머지 35자를 대소문자 없이 비교한다. 건수 0은 패딩이라 무시한다.
 *
 * <p>200 이외 응답(리다이렉트 포함), 빈 응답, 형식이 맞지 않는 줄, 시간 초과(연결 제한, 응답 헤더 제한, 전체 상한 = 연결 + 응답),
 * 연결 실패, 요청 생성 실패는 모두 {@link Result#UNAVAILABLE}이다.
 * 로그에는 원인 종류만 남기고 비밀번호·해시·앞 5자·요청 URL·HTTP 클라이언트 예외(메시지에 URL이 들어갈 수 있음)는 남기지 않는다.
 * Spring {@code RestClient} 대신 JDK {@link HttpClient}를 쓰는 이유도 이것이다(Spring의 I/O 예외 메시지에는 요청 URL이 들어간다).
 */
@Component
class HibpPwnedPasswordsClient implements BreachedPasswordChecker {

    private static final Logger log = LoggerFactory.getLogger(HibpPwnedPasswordsClient.class);

    static final String USER_AGENT = "yhs-live-streaming-backend";
    private static final int PREFIX_LENGTH = 5;
    private static final Pattern ENTRY = Pattern.compile("([0-9A-Fa-f]{35}):([0-9]+)");

    private final HttpClient http;
    private final String rangeBase;
    private final Duration readTimeout;
    private final Duration totalTimeout;

    HibpPwnedPasswordsClient(HibpProperties properties) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        String base = properties.baseUrl().toString();
        this.rangeBase = (base.endsWith("/") ? base : base + "/") + "range/";
        this.readTimeout = properties.readTimeout();
        this.totalTimeout = properties.connectTimeout().plus(properties.readTimeout());
    }

    @Override
    public Result check(String rawPassword) {
        String hex = sha1Hex(rawPassword);
        String prefix = hex.substring(0, PREFIX_LENGTH);
        String suffix = hex.substring(PREFIX_LENGTH);
        CompletableFuture<HttpResponse<String>> future = null;
        try {
            // 요청 생성도 try 안에 둔다: 생성 실패 예외의 메시지에 URL(앞 5자 포함)이 들어갈 수 있다
            HttpRequest request = HttpRequest.newBuilder(URI.create(rangeBase + prefix))
                    .timeout(readTimeout) // 응답 헤더까지
                    .header("Add-Padding", "true")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 전체 상한(연결 + 응답, 본문 포함)
            HttpResponse<String> response = future.get(totalTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                return unavailable("status=" + response.statusCode());
            }
            return match(response.body(), suffix);
        } catch (TimeoutException ex) {
            future.cancel(true);
            return unavailable("timeout");
        } catch (InterruptedException ex) {
            if (future != null) {
                future.cancel(true);
            }
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof HttpTimeoutException) {
                return unavailable("timeout");
            }
            return unavailable(cause instanceof IOException ? "io" : "unexpected");
        } catch (RuntimeException ex) {
            return unavailable("unexpected");
        }
    }

    private static Result match(String body, String suffix) {
        if (body == null) {
            return unavailable("empty");
        }
        int entries = 0;
        boolean breached = false;
        for (String line : body.split("\r?\n")) {
            if (line.isBlank()) {
                continue;
            }
            Matcher matcher = ENTRY.matcher(line.strip());
            if (!matcher.matches()) {
                return unavailable("malformed");
            }
            entries++;
            if (matcher.group(1).equalsIgnoreCase(suffix) && isPositive(matcher.group(2))) {
                breached = true;
            }
        }
        if (entries == 0) {
            return unavailable("empty");
        }
        return breached ? Result.BREACHED : Result.NOT_BREACHED;
    }

    private static boolean isPositive(String digits) {
        return digits.chars().anyMatch(c -> c != '0');
    }

    private static Result unavailable(String reason) {
        log.warn("HIBP 유출 비밀번호 확인 불가: {}", reason);
        return Result.UNAVAILABLE;
    }

    private static String sha1Hex(String rawPassword) {
        byte[] bytes = PasswordNormalization.nfc(rawPassword).getBytes(StandardCharsets.UTF_8);
        byte[] digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-1").digest(bytes);
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1을 쓸 수 없다", ex);
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (digest != null) {
                Arrays.fill(digest, (byte) 0);
            }
        }
    }
}
