package com.yhs.livestreaming.member.app.api;

/** 다른 모듈에 공개하는 회원 계정 상태(MB-D029). member 내부 enum을 노출하지 않으려고 따로 둔다. */
public enum MemberAccountStatus {
    PENDING,
    ACTIVE,
    DISABLED
}
