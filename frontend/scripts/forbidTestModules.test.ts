// @vitest-environment node
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'vite';
import { afterAll, describe, expect, it } from 'vitest';
import { forbidTestModules, isTestModule } from './forbidTestModules';

const root = fileURLToPath(new URL('..', import.meta.url));
const tempDirs: string[] = [];
afterAll(() => tempDirs.forEach((d) => rmSync(d, { recursive: true, force: true })));

function entryImporting(relativeFromRoot: string, useDynamicImport = false): string {
  const dir = mkdtempSync(join(tmpdir(), 'fe01-graph-'));
  tempDirs.push(dir);
  const target = join(root, relativeFromRoot).replaceAll('\\', '/');
  const file = join(dir, 'entry.ts');
  writeFileSync(
    file,
    useDynamicImport ? `export const load = () => import('${target}');\n` : `export * from '${target}';\n`,
  );
  return file;
}

async function buildEntry(entry: string) {
  return build({
    root,
    configFile: false,
    logLevel: 'silent',
    plugins: [forbidTestModules()],
    build: { write: false, rollupOptions: { input: entry } },
  });
}

describe('isTestModule', () => {
  it.each([
    ['D:/x/frontend/src/test/provisionalAuthAdapter.ts', true],
    ['D:\\x\\frontend\\src\\test\\msw\\server.ts', true],
    ['/repo/frontend/src/features/auth/AuthProvider.test.tsx', true],
    ['/repo/frontend/node_modules/msw/lib/node/index.js', true],
    ['/repo/frontend/src/features/auth/AuthProvider.tsx?v=1', false],
    ['/repo/frontend/src/testing-utils.ts', false],
  ])('%s → %s', (id, expected) => {
    expect(isTestModule(id)).toBe(expected);
  });
});

// FE-T015: 운영 빌드 모듈 그래프에 src/test·테스트 파일·msw가 들어오면 빌드를 실패시킨다(FE-D005)
describe('빌드 모듈 그래프 검사', () => {
  it('src/test 모듈을 import하면 빌드가 실패한다', async () => {
    await expect(buildEntry(entryImporting('src/test/provisionalAuthAdapter.ts'))).rejects.toThrow(/테스트 전용 모듈/);
  }, 60_000);

  it('동적 import로 들여와도 실패한다', async () => {
    await expect(buildEntry(entryImporting('src/test/provisionalAuthAdapter.ts', true))).rejects.toThrow(/테스트 전용 모듈/);
  }, 60_000);

  it.each(['?raw', '?url'])('%s 쿼리로 들여와도 실패한다(enforce: pre)', async (query) => {
    const dir = mkdtempSync(join(tmpdir(), 'fe01-graph-'));
    tempDirs.push(dir);
    const target = join(root, 'src/test/provisionalAuthAdapter.ts').replaceAll('\\', '/');
    const entry = join(dir, 'entry.ts');
    writeFileSync(entry, `export { default } from '${target}${query}';\n`);
    await expect(buildEntry(entry)).rejects.toThrow(/테스트 전용 모듈/);
  }, 60_000);

  it('운영 어댑터만 쓰면 빌드된다', async () => {
    await expect(buildEntry(entryImporting('src/features/auth/unavailableAuthAdapter.ts'))).resolves.toBeDefined();
  }, 60_000);
});
