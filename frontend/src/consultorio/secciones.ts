import type { Modulo } from './consultas';

/** Código del módulo en el backend (ModuloDelConsultorio): el nombre de su paquete. */
export type CodigoDeModulo = 'patients' | 'mealplans' | 'scheduling' | 'website' | 'integrations';

export interface Seccion {
  ruta: string;
  etiqueta: string;
  /** null: siempre visible. */
  modulo: CodigoDeModulo | null;
}

/**
 * Secciones del menú. Consultas y el enlace del paciente no tienen sección propia: viven dentro
 * del expediente del paciente.
 */
export const SECCIONES: readonly Seccion[] = [
  { ruta: '/', etiqueta: 'Inicio', modulo: null },
  { ruta: '/pacientes', etiqueta: 'Pacientes', modulo: 'patients' },
  { ruta: '/agenda', etiqueta: 'Agenda', modulo: 'scheduling' },
  { ruta: '/planes', etiqueta: 'Planes de alimentación', modulo: 'mealplans' },
  { ruta: '/sitio', etiqueta: 'Sitio web', modulo: 'website' },
  { ruta: '/integraciones', etiqueta: 'Integraciones', modulo: 'integrations' },
];

export function moduloEncendido(
  modulos: readonly Modulo[] | undefined,
  codigo: CodigoDeModulo,
): boolean {
  // Mientras no se sepa (cargando o error), apagado: es preferible ocultar de más.
  return modulos?.some((m) => m.modulo === codigo && m.habilitado) ?? false;
}

export function seccionesVisibles(modulos: readonly Modulo[] | undefined): Seccion[] {
  return SECCIONES.filter((s) => s.modulo === null || moduloEncendido(modulos, s.modulo));
}
