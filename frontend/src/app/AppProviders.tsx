import { RouterProvider, type createBrowserRouter } from 'react-router';
import type { AuthAdapter } from '../features/auth/authAdapter';
import { AuthProvider } from '../features/auth/AuthProvider';
import type { ApiClient } from '../shared/api/apiClient';

type AppRouter = ReturnType<typeof createBrowserRouter>;

export function AppProviders<P>({ router, adapter, client }: { router: AppRouter; adapter: AuthAdapter<P>; client: ApiClient }) {
  return (
    <AuthProvider adapter={adapter} client={client}>
      <RouterProvider router={router} />
    </AuthProvider>
  );
}
