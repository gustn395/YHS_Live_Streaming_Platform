package com.yhs.livestreaming.member.domain;

/** 계정 상태(MB-D003). PENDING: 가입 직후 첫 OTP 전, ACTIVE: 사용 가능, DISABLED: 사용 중지. */
public enum AccountStatus {
    PENDING,
    ACTIVE,
    DISABLED
}
