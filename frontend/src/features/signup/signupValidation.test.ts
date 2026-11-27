import { describe, expect, it } from 'vitest';
import {
  javaStrip,
  MESSAGES,
  normalizeUsername,
  validateEmail,
  validateNickname,
  validatePassword,
  validatePasswordConfirm,
  validateSignup,
  validateUsername,
} from './signupValidation';

// FE-T024: 클라이언트 입력 검증. 근거는 Backend SignupRequest·SignupPasswordValidator·PasswordPolicy(FE-D022, FE-D031)
describe('username', () => {
  it('ASCII [A-Za-z0-9_]로만 됐을 때만 소문자로 바꾼다', () => {
    expect(normalizeUsername('Tester_01')).toBe('tester_01');
    expect(normalizeUsername('te\u212Aster')).toBe('te\u212Aster'); // KELVIN SIGN은 그대로(Backend도 거부)
    expect(normalizeUsername('Ab-c')).toBe('Ab-c');
  });

  it.each<[string, string | null]>([
    ['', MESSAGES.usernameRequired],
    ['abc', MESSAGES.usernamePattern],
    ['Tester01', null],
    ['a'.repeat(20), null],
    ['a'.repeat(21), MESSAGES.usernamePattern],
    ['\u212Aelvin01', MESSAGES.usernamePattern],
    ['tester-01', MESSAGES.usernamePattern],
  ])('%j → %j', (raw, expected) => {
    expect(validateUsername(raw)).toBe(expected);
  });
});

describe('비밀번호 길이(NFC code point, MB-D026 10~30자)', () => {
  const ok = (password: string) => validatePassword(password, 'tester01');
  it('10자는 통과, 9자는 짧음', () => {
    expect(ok('abcdefghij')).toBeNull();
    expect(ok('abcdefghi')).toBe(MESSAGES.passwordTooShort);
  });
  it('UTF-16이 아니라 code point로 센다', () => {
    expect(ok('😀'.repeat(9))).toBe(MESSAGES.passwordTooShort);
    expect(ok('😀'.repeat(10))).toBeNull();
    expect(ok('😀'.repeat(30))).toBeNull(); // UTF-16으로는 60
    expect(ok('😀'.repeat(31))).toBe(MESSAGES.passwordTooLong);
  });
  it('NFC로 정규화한 뒤 센다', () => {
    expect(ok('é'.repeat(9).normalize('NFD'))).toBe(MESSAGES.passwordTooShort); // NFD로는 18
    expect(ok('é'.repeat(30).normalize('NFD'))).toBeNull(); // NFD로는 60
  });
  it('30자는 통과, 31자는 김', () => {
    expect(ok('a'.repeat(30))).toBeNull();
    expect(ok('a'.repeat(31))).toBe(MESSAGES.passwordTooLong);
  });
  it('공백을 그대로 둔다(Trim 없음)', () => {
    expect(ok('  abcdef  ')).toBeNull(); // 공백 포함 10자
    expect(ok(' abcdefg ')).toBe(MESSAGES.passwordTooShort); // 공백 포함 9자
  });
  it('오류 문구는 Backend PasswordViolation과 같다', () => {
    expect(MESSAGES.passwordTooShort).toBe('비밀번호는 10자 이상이어야 합니다.');
    expect(MESSAGES.passwordTooLong).toBe('비밀번호는 30자 이하여야 합니다.');
  });
  it('비어 있으면 입력 요청', () => {
    expect(ok('')).toBe(MESSAGES.passwordRequired);
  });
});

// Q1=a: ASCII 비밀번호는 Backend PasswordPolicy 문맥 단어 규칙 전체를 재현한다(PasswordPolicyTest 사례)
describe('비밀번호 문맥 단어(ASCII)', () => {
  it.each<[string, string]>([
    ['Long_Username_123', 'long_username_123'],
    ['long_username_1!!!', 'long_username_1'],
    ['1_emanresu_gnol', 'long_username_1'],
    ['yhslivestreaming2026', 'tester01'],
    ['YHS Live Streaming!', 'tester01'],
    ['19736482051983647201', '19736482051983647201'],
    ['1234_5678_9012_3', '1234_5678_9012_3'],
    ['76543210987654321', '12345678901234567'],
    ['12345678901234567!', '12345678901234567'],
  ])('%j (username %j) → 거부', (password, username) => {
    expect(validatePassword(password, username)).toBe(MESSAGES.passwordContextWord);
  });

  it('다른 숫자열·일반 문장은 허용', () => {
    expect(validatePassword('98765432109876543', '12345678901234567')).toBeNull();
    expect(validatePassword('correct horse battery staple', 'tester01')).toBeNull();
  });

  it('username의 ASCII 대문자도 소문자로 바꿔 비교한다', () => {
    expect(validatePassword('long_username_123', 'Long_Username_123')).toBe(MESSAGES.passwordContextWord);
  });

  it('비ASCII가 섞이면 판정하지 않고 서버 응답에 맡긴다', () => {
    expect(validatePassword('yhslivestreaming😀', 'tester01')).toBeNull();
  });

  it('길이 위반이 있으면 문맥 단어는 보지 않는다', () => {
    expect(validatePassword('tester01', 'tester01')).toBe(MESSAGES.passwordTooShort);
  });
});

describe('비밀번호 확인', () => {
  it('원문 그대로 비교한다(NFC로 맞추지 않음)', () => {
    expect(validatePasswordConfirm('abcdefghijklmno', 'abcdefghijklmno')).toBeNull();
    expect(validatePasswordConfirm('é'.repeat(15), 'é'.repeat(15).normalize('NFD'))).toBe(MESSAGES.passwordMismatch);
  });
  it('비어 있으면 입력 요청', () => {
    expect(validatePasswordConfirm('abcdefghijklmno', '')).toBe(MESSAGES.passwordConfirmRequired);
  });
});

describe('닉네임', () => {
  it('Java String.strip()과 같은 공백만 다듬는다', () => {
    expect(javaStrip('  테스터\u3000')).toBe('테스터'); // 전각 공백은 Java 공백
    expect(javaStrip('\u00A0테스터')).toBe('\u00A0테스터'); // NBSP는 Java strip이 지우지 않는다
  });
  it.each<[string, string | null]>([
    ['  테스터  ', null],
    ['', MESSAGES.nicknameRequired],
    ['   ', MESSAGES.nicknameRequired],
    ['가', MESSAGES.nicknamePattern],
    ['\u00A0가', null], // Java strip 뒤 2 code point
    ['\u3000가\u3000', MESSAGES.nicknamePattern],
    ['😀'.repeat(20), null],
    ['😀'.repeat(21), MESSAGES.nicknamePattern],
    ['테스\u200B터', MESSAGES.nicknamePattern], // Cf
    ['테스\u0085터', MESSAGES.nicknamePattern], // Cc
  ])('%j → %j', (raw, expected) => {
    expect(validateNickname(raw)).toBe(expected);
  });
});

describe('이메일(기본 형식만)', () => {
  it.each<[string, string | null]>([
    ['tester@example.com', null],
    ['  Tester@Example.com ', null],
    ['', MESSAGES.emailRequired],
    ['tester.example.com', MESSAGES.emailFormat],
    ['a@@b.com', MESSAGES.emailFormat],
    ['a b@c.com', MESSAGES.emailFormat],
    ['@example.com', MESSAGES.emailFormat],
    ['tester@', MESSAGES.emailFormat],
    [`${'a'.repeat(244)}@example.com`, MESSAGES.emailFormat], // 256자
    // Backend Hibernate @Email 9.1.x 실행 결과(S6 리뷰 지적 7): 따옴표 local part는 공백·@가 있어도 허용한다
    ['"a b"@example.com', null],
    ['"a@b"@example.com', null],
    ['a@b c.com', MESSAGES.emailFormat],
    ['"a b@example.com', MESSAGES.emailFormat], // 따옴표가 닫히지 않음
    // 재리뷰 R5: Hibernate 9.1.6 실행 결과. 점으로 이은 조각마다 따옴표 여부를 본다
    ['"a b".c@x.com', null],
    ['c."a b"@x.com', null],
    ['"a""b"@x.com', MESSAGES.emailFormat],
    ['a"b c"@x.com', MESSAGES.emailFormat],
    ['""@x.com', MESSAGES.emailFormat],
    // 따옴표 안의 백슬래시 이스케이프(Hibernate 9.1.6 실행: 둘 다 VALID)
    ['"a\\"b"@x.com', null],
    ['"a\\\\b"@x.com', null],
  ])('%j → %j', (raw, expected) => {
    expect(validateEmail(raw)).toBe(expected);
  });
});

describe('validateSignup', () => {
  it('필드별 오류만 모아 돌려준다', () => {
    expect(
      validateSignup({ username: 'Tester01', email: 'x', nickname: '테스터', password: 'abcdefghijklmno', passwordConfirm: 'different-value' }),
    ).toEqual({ email: MESSAGES.emailFormat, passwordConfirm: MESSAGES.passwordMismatch });
  });
});
