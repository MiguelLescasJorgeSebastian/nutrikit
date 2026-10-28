import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import {
  accessToken,
  cerrarSesion,
  iniciarSesion,
  renovar,
  suscribirAlTerminar,
} from '../api/sesion';
import { Contexto, type ContextoSesion, type EstadoSesion } from './contexto';

export function ProveedorDeSesion({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [estado, setEstado] = useState<EstadoSesion>(() =>
    accessToken() ? 'activa' : 'verificando',
  );

  // Sesión terminada (cerrar sesión o refresh fallido): nada de los datos del usuario se queda en
  // la caché de TanStack Query.
  useEffect(
    () =>
      suscribirAlTerminar(() => {
        queryClient.clear();
        setEstado('cerrada');
      }),
    [queryClient],
  );

  // Al recargar la página el access token se perdió (estaba en memoria): si la cookie del refresh
  // token sigue vigente, la sesión se recupera sin pedir la contraseña.
  useEffect(() => {
    if (estado !== 'verificando') {
      return;
    }
    let vigente = true;
    renovar().then(
      (sesion) => {
        if (vigente) setEstado(sesion ? 'activa' : 'cerrada');
      },
      () => {
        if (vigente) setEstado('cerrada');
      },
    );
    return () => {
      vigente = false;
    };
  }, [estado]);

  const entrar = useCallback(async (email: string, password: string) => {
    await iniciarSesion(email, password);
    setEstado('activa');
  }, []);

  const valor = useMemo<ContextoSesion>(
    () => ({ estado, entrar, salir: cerrarSesion }),
    [estado, entrar],
  );

  return <Contexto value={valor}>{children}</Contexto>;
}
