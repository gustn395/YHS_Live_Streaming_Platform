package com.yhs.livestreaming.member.out.mail;

/**
 * OTP 메일 발송 실패(MB-D016). SMTP 오류에는 메일 주소·서버 응답이 들어 있을 수 있어 원인·메시지를 싣지 않는다.
 */
public class OtpDeliveryException extends RuntimeException {

    public OtpDeliveryException() {
        super("OTP 메일을 보낼 수 없다", null, false, false);
    }
}
