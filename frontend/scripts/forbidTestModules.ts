import type { Plugin } from 'vite';

/** 운영 빌드에 들어오면 안 되는 모듈: src/test, *.test.*, msw(FE-D005, FE-D003). */
export function isTestModule(id: string): boolean {
  const path = (id.split('?')[0] ?? '').replaceAll('\\', '/');
  return /\/src\/test\//.test(path) || /\.test\.[cm]?[jt]sx?$/.test(path) || /\/node_modules\/msw\//.test(path);
}

/**
 * 운영 빌드 모듈 그래프 검사(FE-T015). ESLint가 못 보는 동적 import·간접 의존으로 들어와도 빌드를 실패시킨다.
 */
export function forbidTestModules(): Plugin {
  return {
    name: 'yhs:forbid-test-modules',
    apply: 'build',
    load(id) {
      if (isTestModule(id)) this.error(`테스트 전용 모듈이 운영 빌드에 포함됐다: ${id}`);
      return null;
    },
  };
}
