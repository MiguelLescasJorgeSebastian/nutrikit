import { z } from 'zod';

const problemDetails = z.object({
  title: z.string().optional(),
  detail: z.string().optional(),
  errores: z.array(z.object({ campo: z.string(), mensaje: z.string() })).optional(),
});

export interface ErrorDeCampo {
  campo: string;
  mensaje: string;
}

/** Error de la API en formato Problem Details (RFC 9457), como lo responde el backend. */
export class ErrorApi extends Error {
  readonly estado: number;
  readonly titulo: string;
  readonly campos: ErrorDeCampo[];
  /** Segundos de Retry-After en un 429. */
  readonly reintentarEn: number | null;

  constructor(
    estado: number,
    titulo: string,
    detalle: string,
    campos: ErrorDeCampo[] = [],
    reintentarEn: number | null = null,
  ) {
    super(detalle);
    this.name = 'ErrorApi';
    this.estado = estado;
    this.titulo = titulo;
    this.campos = campos;
    this.reintentarEn = reintentarEn;
  }

  static async desde(respuesta: Response): Promise<ErrorApi> {
    const cuerpo = problemDetails.safeParse(await respuesta.json().catch(() => null));
    const problema = cuerpo.success ? cuerpo.data : {};
    const reintentar = Number(respuesta.headers.get('Retry-After'));
    return new ErrorApi(
      respuesta.status,
      problema.title ?? respuesta.statusText,
      problema.detail ?? 'Ocurrió un error inesperado.',
      problema.errores ?? [],
      Number.isFinite(reintentar) && reintentar > 0 ? reintentar : null,
    );
  }
}

/** Texto para mostrar al usuario, sea cual sea el error. */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ErrorApi) {
    return error.message;
  }
  return 'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
}
