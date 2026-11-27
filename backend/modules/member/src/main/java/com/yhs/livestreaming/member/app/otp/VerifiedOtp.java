package com.yhs.livestreaming.member.app.otp;

/**
 * OTP 최종 검증 결과(MB-D015, MB-D025). 검증한 Challenge의 회원과 그 Challenge에 묶인 수신 주소다. 수신 주소는 요청 본문이
 * 아니라 Challenge에서만 온다.
 *
 * @param memberId      OTP를 확인한 회원 ID
 * @param deliveryEmail 이번 OTP를 받은 주소(GOOGLE은 로그인 완료 때 이 주소로 이메일을 갱신한다)
 */
public record VerifiedOtp(long memberId, String deliveryEmail) {

    @Override
    public String toString() {
        return "VerifiedOtp[memberId=" + memberId + ", deliveryEmail=****]";
    }
}
