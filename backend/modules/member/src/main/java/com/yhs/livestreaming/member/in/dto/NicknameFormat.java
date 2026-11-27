package com.yhs.livestreaming.member.in.dto;

/** 닉네임 형식(MB-D008): 앞뒤 공백 제거 뒤 2~20자(code point), 제어·서식 문자 금지. 가입·닉네임 수정 요청이 함께 쓴다. */
final class NicknameFormat {

    static final String REGEXP = "^[^\\p{Cc}\\p{Cf}]{2,20}$";
    static final String MESSAGE = "닉네임은 2~20자이고 제어 문자·보이지 않는 서식 문자를 쓸 수 없습니다.";

    private NicknameFormat() {
    }
}
