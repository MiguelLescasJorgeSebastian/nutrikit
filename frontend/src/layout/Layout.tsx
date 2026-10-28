import { NavLink, Outlet } from 'react-router';

import { useSesion } from '../auth/contexto';
import { useConsultorio, useModulos } from '../consultorio/consultas';
import { seccionesVisibles } from '../consultorio/secciones';

export function Layout() {
  const { salir } = useSesion();
  const consultorio = useConsultorio();
  const modulos = useModulos();

  return (
    <div className="flex min-h-svh flex-col md:flex-row">
      <aside className="border-b border-slate-200 bg-white md:w-64 md:border-r md:border-b-0">
        <div className="p-4">
          <p className="text-xs font-semibold tracking-wide text-emerald-700 uppercase">NutriKit</p>
          <p className="truncate font-medium text-slate-900">{consultorio.data?.nombre ?? '…'}</p>
        </div>
        <nav aria-label="Principal" className="px-2 pb-4">
          <ul className="flex gap-1 overflow-x-auto md:flex-col">
            {seccionesVisibles(modulos.data).map((seccion) => (
              <li key={seccion.ruta}>
                <NavLink
                  to={seccion.ruta}
                  end={seccion.ruta === '/'}
                  className={({ isActive }) =>
                    `block rounded-md px-3 py-2 text-sm whitespace-nowrap ${
                      isActive
                        ? 'bg-emerald-50 font-medium text-emerald-800'
                        : 'text-slate-700 hover:bg-slate-100'
                    }`
                  }
                >
                  {seccion.etiqueta}
                </NavLink>
              </li>
            ))}
          </ul>
          {modulos.isError && (
            <p role="alert" className="mt-2 px-3 text-xs text-red-700">
              No se pudieron cargar los módulos del consultorio.
            </p>
          )}
        </nav>
        <div className="px-4 pb-4">
          <button
            type="button"
            onClick={() => void salir()}
            className="text-sm text-slate-600 underline-offset-2 hover:underline"
          >
            Cerrar sesión
          </button>
        </div>
      </aside>
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  );
}
