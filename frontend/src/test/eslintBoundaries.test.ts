// @vitest-environment node
import { fileURLToPath } from 'node:url';
import { ESLint } from 'eslint';
import { describe, expect, it } from 'vitest';

const root = fileURLToPath(new URL('../..', import.meta.url));
const eslint = new ESLint({ cwd: root });

async function restrictedMessages(file: string, code: string): Promise<string[]> {
  const [result] = await eslint.lintText(code, { filePath: `${root}/${file}` });
  return (result?.messages ?? []).filter((m) => m.ruleId === 'no-restricted-imports').map((m) => m.message);
}

// FE-T016: 의존 방향 규칙(app → features → shared), Axios 위치, 운영 코드의 src/test·msw 금지, 상대 경로·Alias 우회 차단(FE-D005, FE-D013)
// 한계: 동적 import()와 간접 의존은 이 규칙이 보장하지 않는다. 운영 Mock 유입은 빌드 모듈 그래프 검사(FE-T015)가 따로 막는다.
describe('ESLint 계층 규칙 — 위반', () => {
  it.each([
    ['shared → features', 'src/shared/api/x.ts', "import { authReducer } from '../../features/auth/authState';"],
    ['shared → features(디렉터리)', 'src/shared/api/x.ts', "import '../../features';"],
    ['shared → app', 'src/shared/api/x.ts', "import { appRoutes } from '../../app/router';"],
    ['shared → app(재수출)', 'src/shared/api/x.ts', "export { apiClient } from '../../app/apiClient';"],
    ['features → app', 'src/features/auth/x.ts', "import { apiClient } from '../../app/apiClient';"],
    ['features → app(전체 재수출)', 'src/features/auth/x.ts', "export * from '../../app/router';"],
    ['shared/api 밖 axios', 'src/features/auth/x.ts', "import axios from 'axios';"],
    ['shared/api 밖 axios 하위 경로', 'src/app/x.ts', "import { AxiosError } from 'axios/unsafe/core/AxiosError.js';"],
    ['운영 코드 → src/test', 'src/app/x.ts', "import { server } from '../test/msw/server';"],
    ['운영 코드 → src/test(features)', 'src/features/auth/x.ts', "import { createProvisionalAuthAdapter } from '../../test/provisionalAuthAdapter';"],
    ['운영 코드 → msw', 'src/app/x.ts', "import { http } from 'msw';"],
    ['Alias 우회 @/', 'src/shared/api/x.ts', "import { authReducer } from '@/features/auth/authState';"],
    ['Alias 우회 src/', 'src/shared/api/x.ts', "import { authReducer } from 'src/features/auth/authState';"],
    ['Alias 우회 ~/', 'src/features/auth/x.ts', "import { apiClient } from '~/app/apiClient';"],
    ['루트 절대 경로 우회', 'src/app/x.ts', "import { server } from '/src/test/msw/server';"],
  ])('%s', async (_name, file, code) => {
    expect(await restrictedMessages(file, code)).not.toHaveLength(0);
  });
});

describe('ESLint 계층 규칙 — 정상 방향은 통과', () => {
  it.each([
    ['shared/api의 axios', 'src/shared/api/x.ts', "import axios from 'axios';"],
    ['shared 안 형제 모듈', 'src/shared/api/x.ts', "import { ApiError } from './apiError';"],
    ['features → shared', 'src/features/auth/x.ts', "import { createApiClient } from '../../shared/api/apiClient';"],
    ['features 안 형제 모듈', 'src/features/auth/x.ts', "import { authReducer } from './authState';"],
    ['app → features·shared', 'src/app/x.ts', "import { RequireAuth } from '../features/auth/RequireAuth';\nimport { createApiClient } from '../shared/api/apiClient';"],
    ['app 안 모듈', 'src/app/x.ts', "import { appRoutes } from './router';"],
    ['main → app', 'src/main.tsx', "import { apiClient } from './app/apiClient';"],
    ['테스트 → src/test·msw', 'src/features/auth/x.test.ts', "import { http } from 'msw';\nimport { server } from '../../test/msw/server';"],
    ['src/test → features', 'src/test/x.ts', "import type { AuthAdapter } from '../features/auth/authAdapter';"],
    ['react-router', 'src/app/x.ts', "import { Link } from 'react-router';"],
  ])('%s', async (_name, file, code) => {
    expect(await restrictedMessages(file, code)).toEqual([]);
  });
});
