/** Vacío en producción: la API está en el mismo origen que el frontend. */
export const API_URL: string = import.meta.env.VITE_API_URL ?? '';

/** Solo en desarrollo; en producción el consultorio sale del subdominio. */
const TENANT: string | undefined = import.meta.env.VITE_TENANT;

export function cabecerasDelConsultorio(): Record<string, string> {
  return TENANT ? { 'X-Tenant': TENANT } : {};
}
