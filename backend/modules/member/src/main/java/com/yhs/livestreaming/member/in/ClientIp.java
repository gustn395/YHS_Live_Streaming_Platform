package com.yhs.livestreaming.member.in;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 제한용 클라이언트 IP(MB-D018). {@code request.getRemoteAddr()}만 쓰고 {@code X-Forwarded-For} 등 신뢰가 확인되지 않은
 * 프록시 헤더는 읽지 않는다. 리버스 프록시 배포 구조가 정해지면 외부 공개 전에 다시 정한다(MB-U06).
 */
final class ClientIp {

    private ClientIp() {
    }

    static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
