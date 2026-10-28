import { Navigate, Outlet, useLocation } from 'react-router';

import { Cargando } from '../layout/Cargando';
import { useSesion } from './contexto';

/** Sin sesión, manda al inicio de sesión y recuerda a dónde iba el usuario. */
export function RutaProtegida() {
  const { estado } = useSesion();
  const ubicacion = useLocation();

  if (estado === 'verificando') {
    return <Cargando />;
  }
  if (estado === 'cerrada') {
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />;
  }
  return <Outlet />;
}
