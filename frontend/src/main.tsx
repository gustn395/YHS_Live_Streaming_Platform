import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { apiClient } from './app/apiClient';
import { AppProviders } from './app/AppProviders';
import { createAppRouter } from './app/router';
import { unavailableAuthAdapter } from './features/auth/unavailableAuthAdapter';

const rootElement = document.getElementById('root');
if (rootElement === null) throw new Error('#root 요소가 없다');

// 운영·일반 개발은 미연결 어댑터만 쓴다. 다른 어댑터를 고르는 환경변수 분기는 두지 않는다(FE-D005).
createRoot(rootElement).render(
  <StrictMode>
    <AppProviders router={createAppRouter()} adapter={unavailableAuthAdapter} client={apiClient} />
  </StrictMode>,
);
