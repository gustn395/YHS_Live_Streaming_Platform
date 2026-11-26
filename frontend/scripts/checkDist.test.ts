// @vitest-environment node
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterAll, describe, expect, it } from 'vitest';
import { PROVISIONAL_MARKER, PROVISIONAL_ME_PATH } from '../src/test/provisionalAuthAdapter.ts';
import { DIST_FORBIDDEN_STRINGS, findForbiddenStrings } from './checkDist';

const dirs: string[] = [];
afterAll(() => dirs.forEach((d) => rmSync(d, { recursive: true, force: true })));

function distWith(files: Record<string, string>): string {
  const dir = mkdtempSync(join(tmpdir(), 'fe01-dist-'));
  dirs.push(dir);
  for (const [name, content] of Object.entries(files)) {
    mkdirSync(join(dir, name, '..'), { recursive: true });
    writeFileSync(join(dir, name), content);
  }
  return dir;
}

// FE-T015: 빌드 결과(dist)에 가안 표지가 남아 있으면 실패한다(FE-D005)
describe('findForbiddenStrings', () => {
  it('검사 문자열은 테스트 가안 어댑터의 표지·경로와 같다', () => {
    expect(DIST_FORBIDDEN_STRINGS).toEqual([PROVISIONAL_MARKER, PROVISIONAL_ME_PATH]);
  });

  it('깨끗한 결과는 빈 목록', () => {
    expect(findForbiddenStrings(distWith({ 'index.html': '<html></html>', 'assets/a.js': 'console.log(1)' }))).toEqual([]);
  });

  it('하위 폴더 파일의 표지도 찾는다', () => {
    const dir = distWith({ 'assets/deep/b.js': `const m = "${PROVISIONAL_MARKER}";`, 'assets/c.js': `fetch("/api${PROVISIONAL_ME_PATH}")` });
    expect(findForbiddenStrings(dir).sort()).toEqual([
      `assets/c.js: ${PROVISIONAL_ME_PATH}`,
      `assets/deep/b.js: ${PROVISIONAL_MARKER}`,
    ]);
  });
});
