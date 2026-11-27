import { fileURLToPath } from 'node:url';
import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';
import { forbidTestModules } from './scripts/forbidTestModules.ts';

// .env·.env.example은 저장소 루트에 있다. FRONTEND_ 접두어 변수만 읽고 번들에는 넣지 않는다(FE-D002).
const repoRoot = fileURLToPath(new URL('..', import.meta.url));
const DEFAULT_API_PROXY_TARGET = 'http://localhost:8080';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, repoRoot, 'FRONTEND_');
  return {
    // forbidTestModules는 build에만 적용된다(FE-T015).
    plugins: [react(), forbidTestModules()],
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target: env.FRONTEND_API_PROXY_TARGET || DEFAULT_API_PROXY_TARGET },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      // e2e/support의 단위 테스트(HIBP 스텁, FE-T040)도 여기서 돈다. Playwright는 *.spec.ts만 수집한다.
      include: ['src/**/*.test.{ts,tsx}', 'scripts/**/*.test.ts', 'e2e/support/**/*.test.ts'],
      restoreMocks: true,
    },
  };
});
