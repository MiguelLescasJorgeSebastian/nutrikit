# 0015. Dónde viven los tokens en el navegador

- Estado: Aceptada
- Fecha: 2026-10-28
- Detalla: [0005](0005-autenticacion-jwt.md)

## Contexto

ADR-0005 define un access token JWT de 15 minutos y un refresh token opaco, rotativo, de 14 días. El panel en React (issue #27) tiene que guardarlos en algún lugar del navegador y renovar el access token sin que el usuario lo note. La app maneja datos de salud: el riesgo que más pesa es un XSS (una dependencia comprometida, un campo mal escapado) que se lleve la sesión.

## Decisión

| | Access token | Refresh token |
| --- | --- | --- |
| Dónde | Memoria de JavaScript (una variable del módulo `api/sesion.ts`) | Cookie `nutrikit_refresh` |
| Atributos | — | `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, sin `Domain` |
| Viaja en | Cabecera `Authorization: Bearer` | Solo a `/api/auth/refresh` y `/api/auth/logout` |
| Al recargar | Se pierde; se pide otro con la cookie | Sigue ahí |

- **Nada en `localStorage` ni `sessionStorage`:** cualquier script de la página los lee. Un XSS se llevaría un refresh token de 14 días a otra máquina.
- **El refresh token nunca pasa por JavaScript:** el backend lo deja en la cookie y ya no lo devuelve en el cuerpo. `HttpOnly` impide leerlo; `Path=/api/auth` evita que viaje en cada llamada a la API; sin `Domain`, la cookie queda atada al subdominio del consultorio que la emitió.
- **CSRF:** la cookie es lo único que el navegador envía solo, y `SameSite=Strict` impide que la envíe en peticiones que empiezan en otro sitio. Por eso la API sigue sin token CSRF. Además, la respuesta de `/refresh` (el access token) no la puede leer otro origen por CORS.
- **Renovación silenciosa:** si una petición recibe 401, el cliente pide un access token nuevo y la repite una vez. Si la renovación falla, la sesión termina, se vacía la caché de TanStack Query y la app vuelve al inicio de sesión.
- **Una renovación a la vez:** cada refresh token sirve una sola vez y reutilizar uno rotado cierra todas las sesiones del usuario (ADR-0005). Varias peticiones con 401 simultáneas comparten una sola promesa de renovación; entre pestañas, un candado de Web Locks hace que la segunda espere y use la cookie nueva. El doble efecto de `StrictMode` en desarrollo cae en el mismo caso.
- **Al abrir la app** se intenta renovar: si la cookie sigue vigente, el usuario entra sin escribir la contraseña.

### CORS

En producción el frontend se sirve desde el subdominio del consultorio, el mismo origen que la API: no hay CORS. En desarrollo Vite corre en `http://localhost:5173` y la API en `:8080`, así que `nutrikit.cors.origenes-permitidos` permite solo ese origen, con credenciales. El filtro CORS va antes de resolver el consultorio y de Spring Security: un preflight no lleva `X-Tenant`, y los errores de esos filtros deben llegar legibles al navegador.

`localhost:5173` y `localhost:8080` son el mismo *sitio* (el puerto no cuenta), así que `SameSite=Strict` no estorba en desarrollo. Solo `Secure` se apaga en el perfil `dev` (`nutrikit.seguridad.refresh.cookie-segura=false`), porque la API corre en http.

### Alternativas descartadas

- **Ambos tokens en `localStorage`:** lo más simple, y lo peor ante un XSS.
- **Access token también en cookie (sesión solo con cookies):** obliga a CSRF en toda la API y a cambiar la cadena de seguridad, que hoy valida el JWT de la cabecera. No compensa mientras el frontend y la API compartan origen.
- **BFF (un servidor que guarda los tokens y da al navegador una cookie de sesión):** lo más robusto, pero agrega un servicio. Si algún día el panel lo necesita, el cambio queda dentro del módulo `api/` del frontend.

## Consecuencias

- **Un XSS sigue siendo grave:** puede hacer peticiones con el access token mientras la página está abierta, pero no se lleva un token que sirva después de 15 minutos ni fuera del navegador. La defensa contra XSS sigue siendo no inyectar HTML y una CSP (pendiente al desplegar).
- **Cada recarga cuesta una renovación** y rota el refresh token. Es una petición corta.
- **Abrir la app en dos subdominios** (dos consultorios) son dos sesiones independientes.
- **El prefijo `__Host-`** endurecería la cookie, pero exige `Path=/` y la haría viajar en todas las peticiones. Se prefirió limitar la ruta.
- **Ocultar del menú un módulo apagado es experiencia de usuario, no seguridad:** el frontend tampoco muestra su ruta si se escribe la URL, pero quien protege es el backend, que responde 404 a los endpoints de un módulo apagado (ADR-0007).
