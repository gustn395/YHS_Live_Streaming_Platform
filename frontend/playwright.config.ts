import os from 'node:os';
import path from 'node:path';
import { defineConfig, devices } from '@playwright/test';

/**
 * 로컬 전용 E2E(FE-D039, FE-D040). CI에서는 실행하지 않는다(FE-U11).
 * 서버(Frontend dev·Backend·PostgreSQL·Redis·Mailpit)는 실행자가 미리 띄운다(webServer 없음). 절차는 docs/frontend/test-spec.md.
 * 결과물은 실패한 테스트의 trace·스크린샷만 저장소 밖 OS 임시 폴더에 남긴다(입력한 비밀번호·OTP가 들어간다).
 */
export default defineConfig({
  testDir: './e2e',
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
