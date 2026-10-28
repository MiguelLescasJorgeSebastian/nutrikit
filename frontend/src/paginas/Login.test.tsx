import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { json, problema, SESION, simularApi } from '../test/apiSimulada';
import { renderizarApp } from '../test/renderizar';

const sinSesion = () => problema(401, 'Sesión inválida', 'Inicia sesión de nuevo.');
const modulos = () => json(200, [{ modulo: 'patients', habilitado: true, nucleo: true }]);
const consultorio = () => json(200, { id: '1', slug: 'demo', nombre: 'Consultorio Demo' });

async function escribir(email: string, password: string) {
  const usuario = userEvent.setup();
  await usuario.type(await screen.findByLabelText('Correo'), email);
  if (password) await usuario.type(screen.getByLabelText('Contraseña'), password);
  await usuario.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('Inicio de sesión', () => {
  it('sin sesión, una ruta protegida manda al inicio de sesión', async () => {
    simularApi({ 'POST /api/auth/refresh': sinSesion });

    const router = renderizarApp('/pacientes');

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('valida el formulario antes de llamar a la API', async () => {
    const api = simularApi({ 'POST /api/auth/refresh': sinSesion });
    renderizarApp('/login');

    await escribir('no-es-correo', '');

    expect(await screen.findByText('Escribe un correo válido')).toBeInTheDocument();
    expect(screen.getByText('Escribe tu contraseña')).toBeInTheDocument();
    expect(api.llamadasA('POST /api/auth/login')).toHaveLength(0);
  });

  it('con credenciales inválidas muestra el error sin recargar la página', async () => {
    simularApi({
      'POST /api/auth/refresh': sinSesion,
      'POST /api/auth/login': () =>
        problema(401, 'Credenciales inválidas', 'Correo o contraseña incorrectos'),
    });
    const router = renderizarApp('/login');

    await escribir('ana@ana.mx', 'incorrecta');

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos');
    expect(router.state.location.pathname).toBe('/login');
    expect(screen.getByLabelText('Correo')).toHaveValue('ana@ana.mx');
  });

  it('con credenciales válidas entra y vuelve a donde iba', async () => {
    const api = simularApi({
      'POST /api/auth/refresh': sinSesion,
      'POST /api/auth/login': () => json(200, SESION),
      'GET /api/consultorio': consultorio,
      'GET /api/consultorio/modulos': modulos,
    });
    const router = renderizarApp('/pacientes');

    await escribir('ana@ana.mx', 'una-contraseña-larga');

    expect(await screen.findByRole('heading', { name: 'Pacientes' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/pacientes');
    expect(api.llamadasA('POST /api/auth/login')[0]?.credentials).toBe('include');
  });

  it('al recargar con la cookie vigente, recupera la sesión sin pedir la contraseña', async () => {
    simularApi({
      'POST /api/auth/refresh': () => json(200, SESION),
      'GET /api/consultorio': consultorio,
      'GET /api/consultorio/modulos': modulos,
    });

    renderizarApp('/');

    expect(await screen.findByText('Bienvenida a Consultorio Demo.')).toBeInTheDocument();
  });
});
