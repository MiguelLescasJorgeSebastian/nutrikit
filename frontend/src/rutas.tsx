import type { RouteObject } from 'react-router';

import { RutaProtegida } from './auth/RutaProtegida';
import { RutaDeModulo } from './consultorio/RutaDeModulo';
import { SECCIONES, type CodigoDeModulo } from './consultorio/secciones';
import { Layout } from './layout/Layout';
import { Inicio } from './paginas/Inicio';
import { Login } from './paginas/Login';
import { NoDisponible } from './paginas/NoDisponible';
import { Pendiente } from './paginas/Pendiente';

/** Cada sección de un módulo va detrás de su RutaDeModulo. Las pantallas llegan en otros issues. */
function rutaDeModulo(ruta: string, etiqueta: string, modulo: CodigoDeModulo): RouteObject {
  return {
    element: <RutaDeModulo modulo={modulo} />,
    children: [{ path: ruta, element: <Pendiente titulo={etiqueta} /> }],
  };
}

export const rutas: RouteObject[] = [
  { path: '/login', element: <Login /> },
  {
    element: <RutaProtegida />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <Inicio /> },
          ...SECCIONES.flatMap((s) =>
            s.modulo ? [rutaDeModulo(s.ruta, s.etiqueta, s.modulo)] : [],
          ),
          { path: '*', element: <NoDisponible /> },
        ],
      },
    ],
  },
];
