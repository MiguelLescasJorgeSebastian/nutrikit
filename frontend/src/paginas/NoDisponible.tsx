import { Link } from 'react-router';

/** Ruta inexistente o de un módulo apagado: el mismo mensaje, como el 404 del backend. */
export function NoDisponible() {
  return (
    <section>
      <h1 className="text-2xl font-semibold text-slate-900">Página no disponible</h1>
      <p className="mt-2 text-slate-600">
        Esta sección no existe o no está activa en tu consultorio.{' '}
        <Link to="/" className="text-emerald-700 underline">
          Volver al inicio
        </Link>
      </p>
    </section>
  );
}
