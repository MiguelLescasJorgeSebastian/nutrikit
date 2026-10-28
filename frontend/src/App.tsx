import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router';

import { ProveedorDeSesion } from './auth/ProveedorDeSesion';
import { crearQueryClient } from './queryClient';
import { rutas } from './rutas';

export function App() {
  const [queryClient] = useState(crearQueryClient);
  const [router] = useState(() => createBrowserRouter(rutas));
  return (
    <QueryClientProvider client={queryClient}>
      <ProveedorDeSesion>
        <RouterProvider router={router} />
      </ProveedorDeSesion>
    </QueryClientProvider>
  );
}
