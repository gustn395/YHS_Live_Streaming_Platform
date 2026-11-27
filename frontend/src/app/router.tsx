import { createBrowserRouter, Outlet, type RouteObject } from 'react-router';
import { RequireAuth } from '../features/auth/RequireAuth';
import { RouteError } from './pages/RouteError';
import { SignupCompletePage } from './pages/SignupCompletePage';
import { SignupPage } from './pages/SignupPage';
import { Forbidden, LoginPlaceholder, NotFound, ProtectedHome, PublicHome } from './pages/SimplePages';

/** React Router data 모드 경로(FE-D012). 공개 경로와 인증 필요 경로(RequireAuth 아래)를 나눈다. */
export const appRoutes: RouteObject[] = [
  {
    path: '/',
    element: <Outlet />,
    errorElement: <RouteError />,
    children: [
      // 공개 레이아웃: 인증 없이 보는 화면
      {
        element: <Outlet />,
        children: [
          { index: true, element: <PublicHome /> },
          { path: 'login', element: <LoginPlaceholder /> },
          // 인증 상태와 관계없이 공개 경로다. 인증된 사용자 처리는 FE-U10
          { path: 'signup', element: <SignupPage /> },
          { path: 'signup/complete', element: <SignupCompletePage /> },
          { path: 'forbidden', element: <Forbidden /> },
        ],
      },
      // 인증 필요 레이아웃
      { element: <RequireAuth />, children: [{ path: 'app', element: <ProtectedHome /> }] },
      { path: '*', element: <NotFound /> },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(appRoutes);
}
