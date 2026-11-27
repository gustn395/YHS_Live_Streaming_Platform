package com.yhs.livestreaming.member.app.oauth;

import java.util.function.Supplier;

/**
 * Google 이름 → 닉네임(MB-D025, MB-D008 규칙): 앞뒤 공백 제거, 제어(Cc)·서식(Cf) 문자 제거, code point 20자로 자른 뒤 다시
 * 앞뒤 공백 제거. 2자 미만이거나 없으면 "user" + 난수 6자리.
 */
public final class GoogleNickname {

    static final int MAX = 20;
    static final int MIN = 2;

    private GoogleNickname() {
    }

    public static String of(String googleName, Supplier<String> sixDigits) {
        if (googleName != null) {
            StringBuilder kept = new StringBuilder();
            googleName.strip().codePoints().filter(cp -> {
                int type = Character.getType(cp);
                return type != Character.CONTROL && type != Character.FORMAT;
            }).limit(MAX).forEach(kept::appendCodePoint);
            String candidate = kept.toString().strip();
            if (candidate.codePointCount(0, candidate.length()) >= MIN) {
                return candidate;
            }
        }
        return "user" + sixDigits.get();
    }
}
