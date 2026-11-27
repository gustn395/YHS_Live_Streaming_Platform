package com.yhs.livestreaming.member.app.password;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 비밀번호 정책(MB-D026 길이, MB-D004, MB-D005). NFC 정규화 뒤 code point 기준 10~30자, 구성 규칙 없음, 공백 허용, Trim 없음.
 * 새로 설정하는 비밀번호에만 쓴다. 로그인은 길이를 검사하지 않으므로 이전 정책(15~64자)으로 만든 비밀번호도 그대로 쓸 수 있다.
 * 유출 비밀번호 검사는 서비스 계층에서 {@link BreachedPasswordChecker}로 한다(MB-D010).
 *
 * <p>문맥 단어 판정: 비밀번호를 소문자로 바꾸고 글자·숫자만 남긴 뒤 끝의 숫자를 뗀 "핵심어"가 username이나 서비스 이름의
 * 핵심어와 같으면(앞뒤를 뒤집은 경우 포함) 거부한다. 예: {@code long_username_1!!!}, {@code yhslivestreaming2026}.
 * 또 소문자·글자·숫자만 남긴 값(끝 숫자를 떼기 전)이 username과 같거나 뒤집어 같으면 거부한다(숫자·밑줄만인 username 대비).
 * 비교는 비밀번호 전체를 기준으로 하며 부분 문자열로 찾지 않는다. 대소문자는 구분하지 않는다.
 */
public class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 30;
    private static final Set<String> SERVICE_WORDS = Set.of("yhslivestreaming", "yhslive", "livestreaming", "yhs");

    /** 위반 목록을 돌려준다. 비어 있으면 통과다. 길이를 어기면 다른 검사는 하지 않는다. */
    public List<PasswordViolation> check(String rawPassword, String username) {
        String password = PasswordNormalization.nfc(rawPassword);
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            return List.of(PasswordViolation.TOO_SHORT);
        }
        if (length > MAX_LENGTH) {
            return List.of(PasswordViolation.TOO_LONG);
        }
        List<PasswordViolation> violations = new ArrayList<>();
        if (isContextWord(password, username)) {
            violations.add(PasswordViolation.CONTEXT_WORD);
        }
        return violations;
    }

    private static boolean isContextWord(String password, String username) {
        String reversedPassword = new StringBuilder(password).reverse().toString();
        // username과 같은 비밀번호(MB-D004)와 그 뒤집기·기호 덧붙이기(MB-D005): 끝 숫자를 떼기 전 값으로 비교해
        // 숫자·밑줄만인 username(핵심어가 빈 문자열)도 잡는다
        if (username != null) {
            String usernameKey = alphanumeric(PasswordNormalization.nfc(username));
            if (!usernameKey.isEmpty() && (usernameKey.equals(alphanumeric(password))
                    || usernameKey.equals(alphanumeric(reversedPassword)))) {
                return true;
            }
        }
        Set<String> contexts = new HashSet<>(SERVICE_WORDS);
        if (username != null) {
            String usernameCore = core(username);
            if (!usernameCore.isEmpty()) {
                contexts.add(usernameCore);
            }
        }
        String forward = core(password);
        String reversed = core(reversedPassword);
        return (!forward.isEmpty() && contexts.contains(forward))
                || (!reversed.isEmpty() && contexts.contains(reversed));
    }

    /** 소문자 → 글자·숫자만 → 끝의 숫자(code point 단위, 보충 평면 숫자 포함) 제거. */
    static String core(String value) {
        String letters = alphanumeric(value);
        int end = letters.length();
        while (end > 0) {
            int codePoint = letters.codePointBefore(end);
            if (!Character.isDigit(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return letters.substring(0, end);
    }

    /** 소문자 → 글자·숫자만. */
    private static String alphanumeric(String value) {
        StringBuilder letters = new StringBuilder();
        value.toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(letters::appendCodePoint);
        return letters.toString();
    }
}
