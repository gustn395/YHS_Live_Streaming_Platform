import os from 'node:os';
import path from 'node:path';
import { defineConfig, devices } from '@playwright/test';

/**
 * E2E(FE-D039, FE-D040). CI 실행은 FE-D044와 .github/workflows/e2e-ci.yml(PR은 @slow 제외, main push·수동은 전체).
 * 서버(Frontend·Backend·PostgreSQL·Redis·Mailpit)는 실행자나 CI가 미리 띄운다(webServer 없음). 로컬 절차는 docs/frontend/test-spec.md.
 * 결과물은 실패한 테스트의 trace·스크린샷만 저장소 밖 OS 임시 폴더에 남긴다(입력한 비밀번호·OTP가 들어간다). CI는 올리지 않는다.
 */
export default defineConfig({
  testDir: './e2e',
  // e2e/support의 *.test.ts는 Vitest 단위 테스트(HIBP 스텁 등)라 수집하지 않는다
  testMatch: '**/*.spec.ts',
  outputDir: path.join(os.tmpdir(), 'yhs-e2e', 'test-results'),
  globalSetup: './e2e/globalSetup.ts',
  // Rate Limit·Mailpit 수신함을 예측할 수 있게 하나씩 실행하고, 흔들리는 테스트를 재시도로 숨기지 않는다
  workers: 1,
  fullyParallel: false,
  retries: 0,
  forbidOnly: true,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
