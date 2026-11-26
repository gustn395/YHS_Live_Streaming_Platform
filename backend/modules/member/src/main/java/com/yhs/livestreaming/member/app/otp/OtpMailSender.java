package com.yhs.livestreaming.member.app.otp;

import java.time.Instant;

/**
 * OTP 메일 발송 포트(MB-D016). 동기로 보내고, 실패하면 예외를 던진다(자동 재시도 없음). 구현은 메일 주소·OTP·SMTP 오류 원문을
 * 로그에 남기지 않는다.
 */
@FunctionalInterface
public interface OtpMailSender {

    void send(String email, String code, Instant expiresAt);
}
