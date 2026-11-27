import js from '@eslint/js';
import { defineConfig, globalIgnores } from 'eslint/config';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// 계층 규칙(FE-D005, FE-D013, frontend/AGENTS.md 5장): app → features → shared 방향만 허용한다.
// regex는 import 문자열 전체에 적용되므로 상대 경로(../../features)와 Alias(@/, ~/, src/, /)를 함께 막는다.
// 한계: 동적 import()와 간접 의존은 이 규칙이 보장하지 않는다. 운영 Mock 유입은 scripts/forbidTestModules가 막는다.
const R = {
  alias: { regex: '^(?:[@~](?:/|$)|src(?:/|$)|/)', message: 'Alias·루트 절대 경로 import 금지. 상대 경로를 쓴다.' },
  test: { regex: '(?:^|/)test(?:/|$)', message: '운영 코드는 src/test를 import하지 않는다(FE-D005).' },
  msw: { regex: '^msw(?:/|$)', message: 'MSW는 테스트에서만 쓴다(FE-D003).' },
  axios: { regex: '^axios(?:/|$)', message: 'axios는 src/shared/api 안에서만 import한다(FE-D013).' },
  features: { regex: '(?:^|/)features(?:/|$)', message: 'shared는 features를 import하지 않는다(app → features → shared).' },
  app: { regex: '(?:^|/)app(?:/|$)', message: 'shared·features는 app을 import하지 않는다(app → features → shared).' },
};
const restrict = (...patterns) => ({ 'no-restricted-imports': ['error', { patterns }] });
const TESTS = ['src/**/*.test.{ts,tsx}', 'src/test/**'];

export default defineConfig([
  globalIgnores(['dist', 'node_modules']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, reactHooks.configs.flat['recommended-latest'], reactRefresh.configs.vite],
    languageOptions: { ecmaVersion: 2023, globals: globals.browser },
  },
  { files: ['vite.config.ts', 'scripts/**/*.ts'], languageOptions: { globals: globals.node } },
  // 로컬 E2E(FE-D039): Node에서 실행하고 앱 소스(src)를 import하지 않는다(실제 화면만 조작한다).
  {
    files: ['playwright.config.ts', 'e2e/**/*.ts'],
    languageOptions: { globals: globals.node },
    rules: restrict(R.alias, { regex: '(?:^|/)src(?:/|$)', message: 'E2E는 앱 소스를 import하지 않는다(실제 화면만 조작).' }),
  },

  // 운영 코드 계층별 규칙. 같은 규칙은 뒤의 설정이 덮어쓰므로 영역마다 전체 목록을 준다.
  { files: ['src/main.tsx', 'src/app/**'], ignores: TESTS, rules: restrict(R.alias, R.test, R.msw, R.axios) },
  { files: ['src/features/**'], ignores: TESTS, rules: restrict(R.alias, R.test, R.msw, R.axios, R.app) },
  { files: ['src/shared/**'], ignores: TESTS, rules: restrict(R.alias, R.test, R.msw, R.axios, R.features, R.app) },
  { files: ['src/shared/api/**'], ignores: TESTS, rules: restrict(R.alias, R.test, R.msw, R.features, R.app) },
  // 테스트는 src/test·msw를 쓸 수 있다. Alias만 막는다.
  { files: TESTS, rules: restrict(R.alias) },
]);
