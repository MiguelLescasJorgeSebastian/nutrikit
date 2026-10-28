import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { json, SESION, simularApi } from '../test/apiSimulada';
import { renderizarApp } from '../test/renderizar';

function api(modulos: { modulo: string; habilitado: boolean }[]) {
  return simularApi({
    'POST /api/auth/refresh': () => json(200, SESION),
    'GET /api/consultorio': () => json(200, { id: '1', slug: 'demo', nombre: 'Consultorio Demo' }),
    'GET /api/consultorio/modulos': () =>
      json(
        200,
        modulos.map((m) => ({ ...m, nucleo: m.modulo === 'patients' })),
      ),
    'POST /api/auth/logout': () => new Response(null, { status: 204 }),
  });
}

async function menu() {
  const nav = await screen.findByRole('navigation', { name: 'Principal' });
  await within(nav).findByRole('link', { name: 'Pacientes' });
  return within(nav)
    .getAllByRole('link')
    .map((enlace) => enlace.textContent);
}

describe('Menú', () => {
  it('muestra solo los módulos encendidos', async () => {
    api([
      { modulo: 'patients', habilitado: true },
      { modulo: 'scheduling', habilitado: false },
      { modulo: 'mealplans', habilitado: true },
      { modulo: 'website', habilitado: false },
      { modulo: 'integrations', habilitado: false },
    ]);

    renderizarApp('/');

    expect(await menu()).toEqual(['Inicio', 'Pacientes', 'Planes de alimentación']);
  });

  it('la URL de un módulo apagado tampoco muestra la sección', async () => {
    api([
      { modulo: 'patients', habilitado: true },
      { modulo: 'scheduling', habilitado: false },
    ]);

    renderizarApp('/agenda');

    expect(
      await screen.findByRole('heading', { name: 'Página no disponible' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Agenda' })).not.toBeInTheDocument();
  });

  it('cerrar sesión vuelve al inicio de sesión', async () => {
    const llamadas = api([{ modulo: 'patients', habilitado: true }]);
    const router = renderizarApp('/');
    await menu();

    await userEvent.setup().click(screen.getByRole('button', { name: 'Cerrar sesión' }));

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(llamadas.llamadasA('POST /api/auth/logout')[0]?.credentials).toBe('include');
  });
});
