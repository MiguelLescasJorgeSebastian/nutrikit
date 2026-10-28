import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';

import { pedir } from '../api/cliente';

const esquemaConsultorio = z.object({
  id: z.string(),
  slug: z.string(),
  nombre: z.string(),
});

const esquemaModulos = z.array(
  z.object({ modulo: z.string(), habilitado: z.boolean(), nucleo: z.boolean() }),
);

export type Modulo = z.infer<typeof esquemaModulos>[number];

export function useConsultorio() {
  return useQuery({
    queryKey: ['consultorio'],
    queryFn: ({ signal }) => pedir('/api/consultorio', esquemaConsultorio, { senal: signal }),
    staleTime: 5 * 60_000,
  });
}

/** El backend cachea los módulos 60 s; aquí tampoco hace falta pedirlos más seguido. */
export function useModulos() {
  return useQuery({
    queryKey: ['consultorio', 'modulos'],
    queryFn: ({ signal }) => pedir('/api/consultorio/modulos', esquemaModulos, { senal: signal }),
    staleTime: 60_000,
  });
}
