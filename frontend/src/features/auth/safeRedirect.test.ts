import { describe, expect, it } from 'vitest';
import { safeRedirect } from './safeRedirect';

const ORIGIN = 'http://localhost:3000';

// FE-T012(일부): 로그인 뒤 돌아갈 from은 안전한 내부 경로만 허용한다(FE-D012)
describe('safeRedirect', () => {
  it.each([
    ['/app', '/app'],
    ['/app/settings?tab=1#top', '/app/settings?tab=1#top'],
    ['/app/%ED%95%9C', '/app/%ED%95%9C'],
  ])('허용: %s', (from, expected) => {
    expect(safeRedirect(from, ORIGIN)).toBe(expected);
  });

  it.each([
    ['없음', null],
    ['빈 문자열', ''],
    ['절대 URL', 'https://evil.example/app'],
    ['스킴 상대', '//evil.example/app'],
    ['역슬래시', '/\\evil.example'],
    ['인코딩된 역슬래시 뒤 다른 출처', '/%5C%5Cevil.example'],
    ['javascript 스킴', 'javascript:alert(1)'],
    ['상대 경로', 'app'],
    ['제어 문자', '/app\n'],
    // 점 세그먼트 정규화 뒤 //로 시작하면 다른 출처로 가는 프로토콜 상대 URL이 된다(S6 리뷰 지적)
    ['점 세그먼트 뒤 //', '/.//evil.example'],
    ['상위 세그먼트 뒤 //', '/x/..//evil.example'],
    ['인코딩된 점 세그먼트 뒤 //', '/%2e//evil.example'],
    ['점 세그먼트', '/app/./x'],
    ['상위 세그먼트로 로그인 우회', '/app/../login'],
    ['로그인 화면', '/login'],
    ['로그인 하위', '/login?from=/app'],
  ])('거부 → "/": %s', (_name, from) => {
    expect(safeRedirect(from, ORIGIN)).toBe('/');
  });
});
