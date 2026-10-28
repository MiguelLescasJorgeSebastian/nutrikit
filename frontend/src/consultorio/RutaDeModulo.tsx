import { Outlet } from 'react-router';

import { Cargando } from '../layout/Cargando';
import { NoDisponible } from '../paginas/NoDisponible';
import { useModulos } from './consultas';
import { moduloEncendido, type CodigoDeModulo } from './secciones';

/**
 * Ocultar el menú no basta: alguien puede escribir la URL. Esta ruta tampoco muestra la sección de
 * un módulo apagado. La protección real es del backend, que responde 404 a sus endpoints.
 */
export function RutaDeModulo({ modulo }: { modulo: CodigoDeModulo }) {
  const { data: modulos, isPending } = useModulos();
  if (isPending) {
    return <Cargando />;
  }
  return moduloEncendido(modulos, modulo) ? <Outlet /> : <NoDisponible />;
}
