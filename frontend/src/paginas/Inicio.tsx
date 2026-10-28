import { useConsultorio } from '../consultorio/consultas';

export function Inicio() {
  const { data: consultorio } = useConsultorio();
  return (
    <section>
      <h1 className="text-2xl font-semibold text-slate-900">Inicio</h1>
      <p className="mt-2 text-slate-600">
        {consultorio ? `Bienvenida a ${consultorio.nombre}.` : 'Bienvenida.'}
      </p>
    </section>
  );
}
