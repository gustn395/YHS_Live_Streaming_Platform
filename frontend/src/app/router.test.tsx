import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { useEffect, useState } from 'react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { unavailableAuthAdapter } from '../features/auth/unavailableAuthAdapter';
import { createApiClient } from '../shared/api/apiClient';
import { isApiError } from '../shared/api/apiError';
import { server } from '../test/msw/server';
import { AppProviders } from './AppProviders';
import { RouteError } from './pages/RouteError';
import { appRoutes } from './router';

function renderApp(path: string) {
  const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
  render(<AppProviders router={router} adapter={unavailableAuthAdapter} client={createApiClient()} />);
  return router;
}

// FE-T012·FE-T015: 운영 어댑터(항상 미인증)로 앱 전체를 조립했을 때
describe('앱 라우팅', () => {
  it('공개 경로 / 는 인증 없이 보인다', async () => {
    renderApp('/');
    expect(await screen.findByRole('heading', { name: 'YHS Live Streaming' })).toBeInTheDocument();
  });

  it('인증 필요 경로 /app 은 미인증이면 /login?from=/app 으로 간다', async () => {
    const router = renderApp('/app');
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(new URLSearchParams(router.state.location.search).get('from')).toBe('/app');
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument();
  });

  it('/forbidden 은 권한 없음 화면이다', async () => {
    renderApp('/forbidden');
    expect(await screen.findByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument();
  });

  it('없는 경로는 404 화면이다', async () => {
    renderApp('/no-such-page');
    expect(await screen.findByRole('heading', { name: '페이지를 찾을 수 없습니다' })).toBeInTheDocument();
  });

  it('루트 경로에 errorElement가 있다', () => {
    expect(appRoutes[0]?.errorElement).toBeDefined();
  });
});

// FE-T013: errorElement는 라우팅(loader·렌더) 오류만 처리한다. 비동기 API 오류는 호출부가 처리한다(FE-D012)
describe('RouteError 역할', () => {
  function renderWith(children: RouteObject[]) {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    const router = createMemoryRouter([{ path: '/', errorElement: <RouteError />, children }], { initialEntries: ['/x'] });
    render(<RouterProvider router={router} />);
  }

  it('loader 오류는 errorElement가 표시한다(오류 상세는 노출하지 않음)', async () => {
    renderWith([
      {
        path: 'x',
        loader: () => {
          throw new Error('secret internal detail');
        },
        element: <p>never</p>,
      },
    ]);
    expect(await screen.findByRole('heading', { name: '화면을 표시하지 못했습니다' })).toBeInTheDocument();
    expect(screen.queryByText(/secret internal detail/)).not.toBeInTheDocument();
  });

  it('렌더 오류는 errorElement가 표시한다', async () => {
    const Boom = () => {
      throw new Error('render failure');
    };
    renderWith([{ path: 'x', element: <Boom /> }]);
    expect(await screen.findByRole('heading', { name: '화면을 표시하지 못했습니다' })).toBeInTheDocument();
  });

  it('비동기 API 오류는 errorElement로 가지 않고 호출부가 처리한다', async () => {
    server.use(http.get('*/api/v1/data', () => HttpResponse.json({ code: 'COMMON_INTERNAL_ERROR' }, { status: 500, headers: { 'Content-Type': 'application/problem+json' } })));
    const client = createApiClient();
    function DataPage() {
      const [message, setMessage] = useState('loading');
      useEffect(() => {
        client.request({ method: 'get', path: '/v1/data', authScope: 'none' }).then(
          () => setMessage('ok'),
          (e: unknown) => setMessage(isApiError(e) ? `api error ${e.failure.kind}` : 'unknown'),
        );
      }, []);
      return <p>{message}</p>;
    }
    renderWith([{ path: 'x', element: <DataPage /> }]);
    expect(await screen.findByText('api error http')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '화면을 표시하지 못했습니다' })).not.toBeInTheDocument();
  });
});
