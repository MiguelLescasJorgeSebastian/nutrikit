import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';

import { ProveedorDeSesion } from '../auth/ProveedorDeSesion';
import { crearQueryClient } from '../queryClient';
import { rutas } from '../rutas';

/** La app completa (sesión, consultas y rutas) empezando en {@code ruta}. */
export function renderizarApp(ruta: string) {
  const router = createMemoryRouter(rutas, { initialEntries: [ruta] });
  render(
    <QueryClientProvider client={crearQueryClient()}>
      <ProveedorDeSesion>
        <RouterProvider router={router} />
      </ProveedorDeSesion>
    </QueryClientProvider>,
  );
  return router;
}
