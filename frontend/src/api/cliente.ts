import type { z } from 'zod';

import { API_URL, cabecerasDelConsultorio } from '../config';
import { ErrorApi } from './errores';
import { accessToken, renovar, terminar } from './sesion';

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface Opciones {
  metodo?: Metodo;
  cuerpo?: unknown;
  senal?: AbortSignal;
}

/**
 * Llama a la API con el access token y valida la respuesta con {@code esquema}.
 *
 * Si el token venció (401), lo renueva sin que el usuario lo note y repite la petición una vez. Si
 * no se puede renovar, termina la sesión: la app vuelve al inicio de sesión.
 */
export async function pedir<T>(
  ruta: string,
  esquema: z.ZodType<T>,
  opciones: Opciones = {},
): Promise<T> {
  let respuesta = await enviar(ruta, opciones);
  if (respuesta.status === 401) {
    const renovada = await renovar();
    if (renovada === null) {
      terminar();
      throw await ErrorApi.desde(respuesta);
    }
    respuesta = await enviar(ruta, opciones);
    if (respuesta.status === 401) {
      terminar();
    }
  }
  if (!respuesta.ok) {
    throw await ErrorApi.desde(respuesta);
  }
  return esquema.parse(respuesta.status === 204 ? undefined : await respuesta.json());
}

function enviar(ruta: string, { metodo = 'GET', cuerpo, senal }: Opciones): Promise<Response> {
  const cabeceras: Record<string, string> = { ...cabecerasDelConsultorio() };
  const token = accessToken();
  if (token) {
    cabeceras.Authorization = `Bearer ${token}`;
  }
  if (cuerpo !== undefined) {
    cabeceras['Content-Type'] = 'application/json';
  }
  return fetch(`${API_URL}${ruta}`, {
    method: metodo,
    headers: cabeceras,
    body: cuerpo === undefined ? null : JSON.stringify(cuerpo),
    signal: senal ?? null,
  });
}
