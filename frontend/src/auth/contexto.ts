import { createContext, useContext } from 'react';

/** verificando: al abrir la app, mientras se intenta recuperar la sesión con la cookie. */
export type EstadoSesion = 'verificando' | 'activa' | 'cerrada';

export interface ContextoSesion {
  estado: EstadoSesion;
  entrar: (email: string, password: string) => Promise<void>;
  salir: () => Promise<void>;
}

export const Contexto = createContext<ContextoSesion | null>(null);

export function useSesion(): ContextoSesion {
  const contexto = useContext(Contexto);
  if (!contexto) {
    throw new Error('useSesion debe usarse dentro de <ProveedorDeSesion>');
  }
  return contexto;
}
