# frontend

Panel del consultorio: React 19 + TypeScript estricto + Vite.

| Pieza                                          | Para qué                                                                              |
| ---------------------------------------------- | ------------------------------------------------------------------------------------- |
| React Router                                   | Rutas; las privadas pasan por `RutaProtegida` y las de cada módulo por `RutaDeModulo` |
| TanStack Query                                 | Datos del servidor: caché, estados de carga y error, cancelación                      |
| React Hook Form + Zod                          | Formularios y su validación; Zod también valida las respuestas de la API              |
| Tailwind CSS                                   | Estilos                                                                               |
| ESLint (typescript-eslint estricto) + Prettier | Calidad y formato                                                                     |
| Vitest + Testing Library                       | Pruebas                                                                               |

## Correr en local

Requisitos: Node 22.13 o más reciente (Jenkins usa Node 24) y el backend corriendo en `:8080` (ver el README de la raíz).

```bash
npm ci
npm run dev        # http://localhost:5173
```

`.env.development` apunta a la API local y manda el consultorio en `X-Tenant` (`VITE_TENANT=nutri-ana`, el del ejemplo del README de la raíz). Para usar otro sin tocar el archivo, crea `.env.development.local` con `VITE_TENANT=<slug>`.

El backend solo acepta peticiones de `http://localhost:5173` en el perfil `dev` (`nutrikit.cors.origenes-permitidos`); por eso Vite usa ese puerto fijo.

## Scripts

| Script                            | Qué hace                                                                       |
| --------------------------------- | ------------------------------------------------------------------------------ |
| `npm run build`                   | `tsc -b` (falla con cualquier error de tipos) y build de producción en `dist/` |
| `npm run lint`                    | ESLint sin advertencias permitidas                                             |
| `npm run format` / `format:check` | Prettier                                                                       |
| `npm test` / `test:watch`         | Vitest                                                                         |

## Sesión

El access token vive solo en memoria y el refresh token en una cookie `HttpOnly` que JavaScript no puede leer ([ADR-0015](../docs/adr/0015-tokens-en-el-navegador.md)). `src/api/cliente.ts` renueva el access token cuando la API responde 401 y repite la petición; si no puede, la app vuelve al inicio de sesión.

## Estructura

```
src/
├── api/          # cliente HTTP, sesión y errores Problem Details
├── auth/         # contexto de sesión y ruta protegida
├── consultorio/  # consultorio actual, módulos y secciones del menú
├── layout/       # marco de la app con el menú
├── paginas/      # pantallas
└── test/         # utilidades de pruebas (API simulada, render con proveedores)
```
