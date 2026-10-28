import { z } from 'zod';

import { API_URL, cabecerasDelConsultorio } from '../config';
import { ErrorApi } from './errores';

/**
 * Sesión del usuario (ver docs/adr/0015-tokens-en-el-navegador.md):
 * - El access token vive solo en memoria de este módulo: no está en localStorage, así que un XSS
 *   no lo encuentra guardado y cerrar la pestaña lo descarta.
 * - El refresh token nunca pasa por JavaScript: el backend lo deja en una cookie HttpOnly y el
 *   navegador la envía sola a /api/auth/refresh y /api/auth/logout.
 */

const esquemaSesion = z.object({
  accessToken: z.string().min(1),
  expiraEn: z.string(),
});

export type Sesion = z.infer<typeof esquemaSesion>;

let actual: Sesion | null = null;
let renovacionEnCurso: Promise<Sesion | null> | null = null;
const alTerminar = new Set<() => void>();

export function accessToken(): string | null {
  return actual?.accessToken ?? null;
}

/** Avisa cuando la sesión termina: al cerrar sesión o cuando ya no se puede renovar. */
export function suscribirAlTerminar(escucha: () => void): () => void {
  alTerminar.add(escucha);
  return () => alTerminar.delete(escucha);
}

export function terminar(): void {
  actual = null;
  alTerminar.forEach((escucha) => {
    escucha();
  });
}

export async function iniciarSesion(email: string, password: string): Promise<Sesion> {
  const respuesta = await fetch(`${API_URL}/api/auth/login`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...cabecerasDelConsultorio() },
    body: JSON.stringify({ email, password }),
  });
  if (!respuesta.ok) {
    throw await ErrorApi.desde(respuesta);
  }
  actual = esquemaSesion.parse(await respuesta.json());
  return actual;
}

export async function cerrarSesion(): Promise<void> {
  try {
    await fetch(`${API_URL}/api/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: cabecerasDelConsultorio(),
    });
  } finally {
    // Aunque el servidor no responda, en este navegador la sesión se acaba.
    terminar();
  }
}

/**
 * Pide un access token nuevo con la cookie del refresh token. Devuelve {@code null} si la cookie
 * ya no sirve; lanza si no hay conexión (la sesión podría seguir siendo válida).
 *
 * Una sola renovación a la vez: cada refresh token se usa una vez y reutilizar uno rotado cierra
 * todas las sesiones del usuario. Si varias peticiones reciben 401 juntas, comparten esta promesa;
 * entre pestañas, el candado de Web Locks hace que la segunda espere y use la cookie nueva.
 */
export function renovar(): Promise<Sesion | null> {
  renovacionEnCurso ??= conCandado(pedirRenovacion).finally(() => {
    renovacionEnCurso = null;
  });
  return renovacionEnCurso;
}

async function pedirRenovacion(): Promise<Sesion | null> {
  const respuesta = await fetch(`${API_URL}/api/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
    headers: cabecerasDelConsultorio(),
  });
  if (respuesta.status === 401) {
    actual = null;
    return null;
  }
  if (!respuesta.ok) {
    throw await ErrorApi.desde(respuesta);
  }
  actual = esquemaSesion.parse(await respuesta.json());
  return actual;
}

function conCandado<T>(tarea: () => Promise<T>): Promise<T> {
  if (!('locks' in navigator)) {
    return tarea();
  }
  return navigator.locks.request('nutrikit-renovar-sesion', tarea);
}
