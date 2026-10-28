import { vi } from 'vitest';

type Manejador = (peticion: Request) => Response | Promise<Response>;

export function json(estado: number, cuerpo: unknown, cabeceras: Record<string, string> = {}) {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json', ...cabeceras },
  });
}

export function problema(estado: number, title: string, detail: string) {
  return new Response(JSON.stringify({ status: estado, title, detail }), {
    status: estado,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

/**
 * Sustituye fetch por un mapa "MÉTODO /ruta" → respuesta. Una ruta sin manejador falla la prueba,
 * así ninguna petición inesperada pasa desapercibida.
 */
export function simularApi(manejadores: Record<string, Manejador>) {
  const llamadas: Request[] = [];
  const fetchSimulado = vi.fn((entrada: string, init?: RequestInit) => {
    const peticion = new Request(new URL(entrada, 'http://api.test'), init);
    llamadas.push(peticion);
    const clave = `${peticion.method} ${new URL(peticion.url).pathname}`;
    const manejador = manejadores[clave];
    if (!manejador) {
      throw new Error(`Petición no simulada: ${clave}`);
    }
    return Promise.resolve(manejador(peticion));
  });
  vi.stubGlobal('fetch', fetchSimulado);
  return {
    llamadas,
    llamadasA: (clave: string) =>
      llamadas.filter((p) => `${p.method} ${new URL(p.url).pathname}` === clave),
  };
}

export const SESION = { accessToken: 'token-1', tipo: 'Bearer', expiraEn: '2026-10-28T18:45:00Z' };
