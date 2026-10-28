/** Sección de un módulo encendido cuya pantalla todavía no existe. */
export function Pendiente({ titulo }: { titulo: string }) {
  return (
    <section>
      <h1 className="text-2xl font-semibold text-slate-900">{titulo}</h1>
      <p className="mt-2 text-slate-600">Esta sección estará disponible pronto.</p>
    </section>
  );
}
