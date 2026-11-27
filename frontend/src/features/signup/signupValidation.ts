/**
 * 회원가입 입력 검증(FE-D022, FE-D031). 규칙 원본은 Backend(SignupRequest, SignupPasswordValidator, PasswordPolicy)이고
 * 최종 판정은 서버다. 정확히 재현할 수 있는 규칙만 여기서 판정하고, 나머지는 서버 응답(errors[])으로 표시한다.
 */

/** Backend 문구와 같은 것은 그대로 쓴다(필드 오류가 서버에서 와도 같은 문구가 보이게). */
export const MESSAGES = {
  usernameRequired: '아이디를 입력해 주세요.',
  usernamePattern: '아이디는 영문 소문자·숫자·_ 4~20자입니다.',
  passwordRequired: '비밀번호를 입력해 주세요.',
  passwordTooShort: '비밀번호는 15자 이상이어야 합니다.',
  passwordTooLong: '비밀번호는 64자 이하여야 합니다.',
  passwordContextWord: '아이디나 서비스 이름을 바탕으로 한 비밀번호는 쓸 수 없습니다.',
  passwordConfirmRequired: '비밀번호를 한 번 더 입력해 주세요.',
  passwordMismatch: '비밀번호 확인이 일치하지 않습니다.',
  nicknameRequired: '닉네임을 입력해 주세요.',
  nicknamePattern: '닉네임은 2~20자이고 제어 문자·보이지 않는 서식 문자를 쓸 수 없습니다.',
  emailRequired: '이메일을 입력해 주세요.',
  emailFormat: '이메일 형식을 확인해 주세요.',
} as const;

export interface SignupInput {
  username: string;
  email: string;
  nickname: string;
  password: string;
  passwordConfirm: string;
}

export type SignupField = keyof SignupInput;
export type SignupFieldErrors = Partial<Record<SignupField, string>>;

const PASSWORD_MIN = 15;
const PASSWORD_MAX = 64;
const EMAIL_MAX_UTF16 = 255; // Backend @Size(max = 255)는 Java String 길이(UTF-16 단위)
const SERVICE_WORDS = new Set(['yhslivestreaming', 'yhslive', 'livestreaming', 'yhs']);

const ASCII_USERNAME_CHARS = /^[A-Za-z0-9_]*$/;
const USERNAME_PATTERN = /^[a-z0-9_]{4,20}$/;
const NICKNAME_PATTERN = /^[^\p{Cc}\p{Cf}]{2,20}$/u;
/** ASCII(U+0000~U+007F)로만 됐는지. */
function isAsciiOnly(value: string): boolean {
  for (let i = 0; i < value.length; i += 1) if (value.charCodeAt(i) > 0x7f) return false;
  return true;
}

// Java Character.isWhitespace: 공백 구분자(Zs·Zl·Zp, 단 NBSP U+00A0·U+2007·U+202F 제외)와 U+0009~U+000D·U+001C~U+001F
const UNICODE_SEPARATOR = /^[\p{Zs}\p{Zl}\p{Zp}]$/u;
const NON_BREAKING = new Set([0x00a0, 0x2007, 0x202f]);
function isJavaWhitespace(char: string): boolean {
  const cp = char.codePointAt(0) ?? 0;
  if ((cp >= 0x09 && cp <= 0x0d) || (cp >= 0x1c && cp <= 0x1f)) return true;
  return UNICODE_SEPARATOR.test(char) && !NON_BREAKING.has(cp);
}

/** Backend처럼 ASCII [A-Za-z0-9_]로만 됐을 때만 소문자로 바꾼다(비ASCII를 바꾸면 형식 검사를 우회할 수 있다). */
export function normalizeUsername(raw: string): string {
  return ASCII_USERNAME_CHARS.test(raw) ? raw.toLowerCase() : raw;
}

/** Java String.strip()과 같은 기준으로 앞뒤 공백을 지운다(JS trim과 지우는 문자가 다르다). */
export function javaStrip(raw: string): string {
  const chars = [...raw];
  let start = 0;
  let end = chars.length;
  while (start < end && isJavaWhitespace(chars[start]!)) start += 1;
  while (end > start && isJavaWhitespace(chars[end - 1]!)) end -= 1;
  return chars.slice(start, end).join('');
}

export function validateUsername(raw: string): string | null {
  if (raw === '') return MESSAGES.usernameRequired;
  return USERNAME_PATTERN.test(normalizeUsername(raw)) ? null : MESSAGES.usernamePattern;
}

/** ASCII 전용: 소문자 → 글자·숫자만. */
function alphanumeric(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

/** ASCII 전용: alphanumeric 뒤 끝의 숫자를 뗀 핵심어. */
function core(value: string): string {
  return alphanumeric(value).replace(/[0-9]+$/, '');
}

/**
 * Backend PasswordPolicy의 문맥 단어 판정을 ASCII 입력에서만 재현한다(FE-D031, Q1=a).
 * 비ASCII가 섞이면 Java·JS의 대소문자 변환과 Unicode 판정이 달라질 수 있어 판정하지 않는다(null).
 */
function isContextWord(password: string, username: string): boolean | null {
  if (!isAsciiOnly(password) || !isAsciiOnly(username)) return null;
  const reversed = [...password].reverse().join('');
  const usernameKey = alphanumeric(username);
  if (usernameKey !== '' && (usernameKey === alphanumeric(password) || usernameKey === alphanumeric(reversed))) {
    return true;
  }
  const contexts = new Set(SERVICE_WORDS);
  const usernameCore = core(username);
  if (usernameCore !== '') contexts.add(usernameCore);
  const forward = core(password);
  const backward = core(reversed);
  return (forward !== '' && contexts.has(forward)) || (backward !== '' && contexts.has(backward));
}

export function validatePassword(password: string, username: string): string | null {
  if (password === '') return MESSAGES.passwordRequired;
  const length = [...password.normalize('NFC')].length;
  if (length < PASSWORD_MIN) return MESSAGES.passwordTooShort;
  if (length > PASSWORD_MAX) return MESSAGES.passwordTooLong;
  return isContextWord(password, normalizeUsername(username)) === true ? MESSAGES.passwordContextWord : null;
}

/** Backend는 원문을 equals로 비교한다. NFC로 맞추지 않는다. */
export function validatePasswordConfirm(password: string, confirm: string): string | null {
  if (confirm === '') return MESSAGES.passwordConfirmRequired;
  return password === confirm ? null : MESSAGES.passwordMismatch;
}

export function validateNickname(raw: string): string | null {
  const nickname = javaStrip(raw);
  if (nickname === '') return MESSAGES.nicknameRequired;
  return NICKNAME_PATTERN.test(nickname) ? null : MESSAGES.nicknamePattern;
}

/** 기본 형식과 길이만 본다. Hibernate @Email의 세부 판정은 서버 응답으로 표시한다(FE-D031). */
export function validateEmail(raw: string): string | null {
  const email = javaStrip(raw);
  if (email === '') return MESSAGES.emailRequired;
  if (email.length > EMAIL_MAX_UTF16) return MESSAGES.emailFormat;
  // 마지막 @로 나눈다. Backend(Hibernate @Email)는 점으로 이은 local part 조각 중 따옴표로 감싼 조각 안의 공백·@를 허용한다
  const at = email.lastIndexOf('@');
  const local = email.slice(0, at);
  const domain = email.slice(at + 1);
  if (at <= 0 || domain === '' || /[\s@]/u.test(domain)) return MESSAGES.emailFormat;
  return localPartSegments(local)?.every(isAcceptableLocalSegment) === true ? null : MESSAGES.emailFormat;
}

/** local part를 따옴표 밖의 점으로 나눈다. 따옴표 안의 백슬래시는 다음 글자를 이스케이프한다. 따옴표가 닫히지 않으면 null. */
function localPartSegments(local: string): string[] | null {
  const segments: string[] = [];
  let current = '';
  let inQuote = false;
  let escaped = false;
  for (const char of local) {
    if (escaped) {
      current += char;
      escaped = false;
      continue;
    }
    if (inQuote && char === '\\') {
      current += char;
      escaped = true;
      continue;
    }
    if (char === '"') inQuote = !inQuote;
    if (char === '.' && !inQuote) {
      segments.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  if (inQuote || escaped) return null;
  segments.push(current);
  return segments;
}

/**
 * 따옴표 조각은 "비어 있지 않고, 이스케이프하지 않은 따옴표가 안에 없음", 일반 조각은 공백·@·따옴표 없음만 본다.
 * 이스케이프한 글자의 허용 여부 등 그 밖의 세부는 서버 판정에 맡긴다(FE-D031).
 */
function isAcceptableLocalSegment(segment: string): boolean {
  if (segment.startsWith('"')) {
    const inner = segment.slice(1, -1).replace(/\\./gu, 'x');
    return segment.length >= 3 && segment.endsWith('"') && !inner.includes('"');
  }
  return !/[\s@"]/u.test(segment);
}

export function validateField(field: SignupField, input: SignupInput): string | null {
  switch (field) {
    case 'username':
      return validateUsername(input.username);
    case 'email':
      return validateEmail(input.email);
    case 'nickname':
      return validateNickname(input.nickname);
    case 'password':
      return validatePassword(input.password, input.username);
    case 'passwordConfirm':
      return validatePasswordConfirm(input.password, input.passwordConfirm);
  }
}

export const SIGNUP_FIELDS: readonly SignupField[] = ['username', 'email', 'nickname', 'password', 'passwordConfirm'];

export function validateSignup(input: SignupInput): SignupFieldErrors {
  const errors: SignupFieldErrors = {};
  for (const field of SIGNUP_FIELDS) {
    const message = validateField(field, input);
    if (message !== null) errors[field] = message;
  }
  return errors;
}
