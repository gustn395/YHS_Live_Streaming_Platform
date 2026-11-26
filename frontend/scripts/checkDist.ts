import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

/** 테스트 가안 어댑터(src/test/provisionalAuthAdapter.ts)의 표지·경로. 운영 빌드 결과에 있으면 안 된다(FE-D005). */
export const DIST_FORBIDDEN_STRINGS = ['PROVISIONAL-AUTH-MOCK', '/v1/provisional/me'] as const;

function listFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = join(dir, entry.name);
    return entry.isDirectory() ? listFiles(full) : [full];
  });
}

/** dist 안에서 금지 문자열을 찾아 "상대경로: 문자열" 목록으로 돌려준다(FE-T015). */
export function findForbiddenStrings(distDir: string): string[] {
  const found: string[] = [];
  for (const file of listFiles(distDir)) {
    const content = readFileSync(file, 'utf8');
    for (const s of DIST_FORBIDDEN_STRINGS) {
      if (content.includes(s)) found.push(`${relative(distDir, file).replaceAll('\\', '/')}: ${s}`);
    }
  }
  return found;
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const distDir = fileURLToPath(new URL('../dist', import.meta.url));
  const found = findForbiddenStrings(distDir);
  if (found.length > 0) {
    console.error(`빌드 결과에 테스트 가안 표지가 있다(FE-T015):\n${found.join('\n')}`);
    process.exit(1);
  }
  console.log('check-dist: 가안 표지 없음');
}
