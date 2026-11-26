package com.yhs.livestreaming.member.out.mail;

import com.yhs.livestreaming.member.app.otp.OtpMailSender;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP OTP 메일 발송 어댑터(MB-D016). 동기 발송, 자동 재시도 없음. 연결·읽기·쓰기 타임아웃은 {@code spring.mail.properties}의
 * {@code mail.smtp.*timeout} 설정값이다.
 *
 * <ul>
 *     <li>개발: Mailpit(인증·TLS 없음). 운영 SMTP 계정이 필요 없다.</li>
 *     <li>운영: Gmail SMTP(587, 인증 + STARTTLS 필수). {@code spring.mail.username}·{@code password}(앱 비밀번호)와
 *     {@code mail.smtp.auth}·{@code mail.smtp.starttls.required}를 환경으로 켠다.</li>
 * </ul>
 * 실패는 {@link OtpDeliveryException}(원인·메일 주소·OTP 없음)으로 바꾼다. 로그를 남기지 않는다(호출자가 회원 ID만 남긴다).
 */
public class SmtpOtpMailSender implements OtpMailSender {

    static final String SUBJECT = "[YHS Live] 로그인 인증 번호";
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final JavaMailSender mailSender;
    private final MemberOtpMailProperties properties;

    public SmtpOtpMailSender(JavaMailSender mailSender, MemberOtpMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String email, String code, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText("로그인 인증 번호: " + code + "\n\n"
                + "이 번호는 " + TIME.format(expiresAt) + "(한국 시간)까지 쓸 수 있습니다.\n"
                + "본인이 로그인하지 않았다면 이 메일을 무시하고 비밀번호를 바꿔 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException | IllegalStateException ex) {
            throw new OtpDeliveryException();
        }
    }
}
