import { z } from 'zod';

import { json, problema, SESION, simularApi } from '../test/apiSimulada';
import { pedir } from './cliente';
import { ErrorApi } from './errores';
import { accessToken, iniciarSesion, suscribirAlTerminar } from './sesion';

const esquema = z.object({ nombre: z.string() });
const vencido = () => problema(401, 'Unauthorized', 'Token vencido');

async function conSesion() {
  await iniciarSesion('ana@ana.mx', 'contraseña');
}

describe('pedir', () => {
  it('manda el access token y valida la respuesta', async () => {
    const api = simularApi({
      'POST /api/auth/login': () => json(200, SESION),
      'GET /api/consultorio': () => json(200, { nombre: 'Consultorio Ana' }),
    });
    await conSesion();

    await expect(pedir('/api/consultorio', esquema)).resolves.toEqual({
      nombre: 'Consultorio Ana',
    });
    expect(api.llamadasA('GET /api/consultorio')[0]?.headers.get('Authorization')).toBe(
      'Bearer token-1',
    );
  });

  it('con el access token vencido, lo renueva sin que se note y repite la petición', async () => {
    const api = simularApi({
      'POST /api/auth/login': () => json(200, SESION),
      'POST /api/auth/refresh': () => json(200, { ...SESION, accessToken: 'token-2' }),
      'GET /api/consultorio': (peticion) =>
        peticion.headers.get('Authorization') === 'Bearer token-2'
          ? json(200, { nombre: 'Consultorio Ana' })
          : vencido(),
    });
    await conSesion();

    await expect(pedir('/api/consultorio', esquema)).resolves.toEqual({
      nombre: 'Consultorio Ana',
    });
    expect(accessToken()).toBe('token-2');
    expect(api.llamadasA('POST /api/auth/refresh')[0]?.credentials).toBe('include');
  });

  it('varias peticiones con 401 a la vez comparten una sola renovación', async () => {
    // Dos renovaciones con el mismo refresh token serían una reutilización: el backend cerraría
    // todas las sesiones del usuario.
    const api = simularApi({
      'POST /api/auth/login': () => json(200, SESION),
      'POST /api/auth/refresh': () => json(200, { ...SESION, accessToken: 'token-2' }),
      'GET /api/consultorio': (peticion) =>
        peticion.headers.get('Authorization') === 'Bearer token-2'
          ? json(200, { nombre: 'Consultorio Ana' })
          : vencido(),
    });
    await conSesion();

    await Promise.all([
      pedir('/api/consultorio', esquema),
      pedir('/api/consultorio', esquema),
      pedir('/api/consultorio', esquema),
    ]);

    expect(api.llamadasA('POST /api/auth/refresh')).toHaveLength(1);
  });

  it('si la renovación falla, termina la sesión', async () => {
    simularApi({
      'POST /api/auth/login': () => json(200, SESION),
      'POST /api/auth/refresh': () => problema(401, 'Sesión inválida', 'Inicia sesión de nuevo.'),
      'GET /api/consultorio': vencido,
    });
    await conSesion();
    const alTerminar = vi.fn();
    const desuscribir = suscribirAlTerminar(alTerminar);

    const error = await pedir('/api/consultorio', esquema).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ErrorApi);
    expect((error as ErrorApi).estado).toBe(401);
    expect(alTerminar).toHaveBeenCalledOnce();
    expect(accessToken()).toBeNull();
    desuscribir();
  });

  it('un error de la API llega como ErrorApi con su detalle', async () => {
    simularApi({
      'POST /api/auth/login': () => json(200, SESION),
      'GET /api/consultorio': () => problema(404, 'Not Found', 'El consultorio no existe.'),
    });
    await conSesion();

    await expect(pedir('/api/consultorio', esquema)).rejects.toMatchObject({
      estado: 404,
      message: 'El consultorio no existe.',
    });
  });
});
