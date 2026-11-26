package com.yhs.livestreaming.member.app.password;

import java.text.Normalizer;

/** 비밀번호 정규화 규칙의 한 곳(MB-D004·MB-D006·MB-D010): NFC. 해시 포맷의 정규화 버전({@code nfc})과 함께 바뀐다. */
public final class PasswordNormalization {

    private PasswordNormalization() {
    }

    public static String nfc(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
