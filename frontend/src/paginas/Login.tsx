import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { z } from 'zod';

import { mensajeDeError } from '../api/errores';
import { useSesion } from '../auth/contexto';

const esquema = z.object({
  email: z.email('Escribe un correo válido'),
  password: z.string().min(1, 'Escribe tu contraseña'),
});

type Credenciales = z.infer<typeof esquema>;

function destinoTrasEntrar(estado: unknown): string {
  if (typeof estado === 'object' && estado !== null && 'desde' in estado) {
    const { desde } = estado;
    if (typeof desde === 'string' && desde.startsWith('/') && desde !== '/login') {
      return desde;
    }
  }
  return '/';
}

export function Login() {
  const { estado, entrar } = useSesion();
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const destino = destinoTrasEntrar(ubicacion.state);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Credenciales>({ resolver: zodResolver(esquema) });

  const inicio = useMutation({
    mutationFn: ({ email, password }: Credenciales) => entrar(email, password),
    onSuccess: () => {
      void navegar(destino, { replace: true });
    },
  });

  if (estado === 'activa') {
    return <Navigate to={destino} replace />;
  }

  return (
    <main className="flex min-h-svh items-center justify-center p-4">
      <form
        noValidate
        onSubmit={(evento) => {
          void handleSubmit((datos) => {
            inicio.mutate(datos);
          })(evento);
        }}
        className="w-full max-w-sm space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm"
      >
        <div>
          <p className="text-xs font-semibold tracking-wide text-emerald-700 uppercase">NutriKit</p>
          <h1 className="text-xl font-semibold text-slate-900">Iniciar sesión</h1>
        </div>

        {inicio.isError && (
          <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-800">
            {mensajeDeError(inicio.error)}
          </p>
        )}

        <Campo etiqueta="Correo" error={errors.email?.message} id="email">
          <input
            id="email"
            type="email"
            autoComplete="username"
            aria-invalid={errors.email ? true : undefined}
            aria-describedby={errors.email ? 'email-error' : undefined}
            className={claseDeEntrada}
            {...register('email')}
          />
        </Campo>

        <Campo etiqueta="Contraseña" error={errors.password?.message} id="password">
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={errors.password ? true : undefined}
            aria-describedby={errors.password ? 'password-error' : undefined}
            className={claseDeEntrada}
            {...register('password')}
          />
        </Campo>

        <button
          type="submit"
          disabled={inicio.isPending}
          className="w-full rounded-md bg-emerald-700 px-4 py-2 font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
        >
          {inicio.isPending ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </main>
  );
}

const claseDeEntrada =
  'mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 focus:border-emerald-600 focus:ring-1 focus:ring-emerald-600 focus:outline-none aria-invalid:border-red-600';

function Campo({
  etiqueta,
  error,
  id,
  children,
}: {
  etiqueta: string;
  error: string | undefined;
  id: string;
  children: ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {etiqueta}
      </label>
      {children}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}
