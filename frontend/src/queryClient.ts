import { QueryClient } from '@tanstack/react-query';

import { ErrorApi } from './api/errores';

export function crearQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Un 4xx no se arregla reintentando (y el 401 ya lo resolvió el cliente renovando).
        retry: (intentos, error) =>
          !(error instanceof ErrorApi && error.estado < 500) && intentos < 2,
        refetchOnWindowFocus: false,
      },
    },
  });
}
