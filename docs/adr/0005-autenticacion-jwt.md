# 0005. Autenticación con JWT propios y refresh tokens rotativos

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

La nutrióloga y su equipo entran al panel con correo y contraseña (RF-IAM-01) con roles distintos (RF-IAM-02). RNF-SEG-01 pide hash de contraseñas, JWT de vida corta y refresh tokens rotativos; RNF-SEG-05, limitar intentos de inicio de sesión. Cada usuario pertenece a un consultorio y su token no debe servir en otro.

## Decisión

### Tokens

| | Access token | Refresh token |
| --- | --- | --- |
| Formato | JWT firmado con HS256 | 256 bits aleatorios, opaco |
| Vida | 15 minutos (máximo, validado al arrancar) | 14 días |
| Dónde vive | Solo en el cliente; la API no guarda estado | En `refresh_token`, solo su SHA-256 |
| Claims | `iss`, `sub` (id del usuario), `jti`, `iat`, `exp`, `tenant_id`, `rol` | — |

El JWT no lleva correo, nombre ni datos de salud: cualquiera puede decodificarlo sin la clave.

La clave HMAC sale de `NUTRIKIT_JWT_SECRET` (Base64, mínimo 256 bits). Sin ella la app no arranca; solo el perfil `dev` permite una clave efímera generada al iniciar.

**HS256 y no RS256:** hoy una sola aplicación firma y valida. Si un módulo se separa en otro servicio, conviene RS256 para que los demás validen con la clave pública sin poder firmar.

### Rotación de refresh tokens

Cada refresh token sirve una vez: al usarlo se revoca y se emite un par nuevo. Si llega uno ya revocado, alguien lo está reutilizando (un robo o una copia) y se revocan **todas** las sesiones del usuario. La búsqueda bloquea la fila (`SELECT ... FOR UPDATE`) para que dos renovaciones simultáneas con el mismo token no obtengan ambas un par nuevo.

`refresh_token` lleva `tenant_id` y RLS, aunque el modelo de datos v0.1 no lo incluye, por la regla del núcleo: toda tabla de negocio tiene `tenant_id`.

### Consultorio y roles

- El consultorio de la petición se resuelve **antes** de Spring Security (ADR-0004). Toda ruta `/api/**` de consultorio exige que el `tenant_id` del token sea el de la petición; si no, 403.
- El inicio de sesión ocurre en el consultorio del subdominio, así que el mismo correo puede existir en dos consultorios.
- El claim `rol` se convierte en la autoridad `ROLE_<rol>`.

### Operador de la plataforma

El operador da de alta consultorios y sus usuarios. No pertenece a ningún consultorio, así que no está en la tabla `usuario`: sus credenciales vienen de `NUTRIKIT_OPERADOR_EMAIL` y `NUTRIKIT_OPERADOR_PASSWORD_HASH`. Recibe solo un access token con `rol=OPERADOR` y sin `tenant_id`; al vencer, inicia sesión de nuevo. Cuando haya más de un operador, se migra a un proveedor de identidad (Keycloak) o a una tabla propia.

### Contraseñas

`DelegatingPasswordEncoder` con BCrypt: cada hash lleva el prefijo del algoritmo (`{bcrypt}...`), así que se puede pasar a Argon2 sin invalidar los existentes. Si el correo no existe, se compara contra un hash señuelo para que el tiempo de respuesta no revele qué correos están registrados. Los DTO y entidades sobrescriben `toString` para que la contraseña, el hash y los tokens no lleguen a un log.

### Límite de intentos

Ventana deslizante en memoria: 5 fallos por correo (en su consultorio) y 20 por IP cada 15 minutos. Al llegar al límite se responde 429 con `Retry-After`, aunque la contraseña sea correcta. Un acceso correcto reinicia el conteo del correo, no el de la IP.

## Consecuencias

- **Revocar un access token** no es posible: un usuario desactivado conserva el acceso hasta 15 minutos. Es el costo de no consultar la base en cada petición.
- **Varias réplicas:** el límite de intentos vive en memoria de cada instancia; antes de escalar horizontalmente hay que moverlo a Redis (por ejemplo, con Bucket4j).
- **IP real detrás de un proxy:** en producción hay que configurar `server.forward-headers-strategy` para que `getRemoteAddr()` sea la IP del cliente y no la del balanceador.
- **Limpieza:** los refresh tokens expirados y revocados se acumulan; falta un job que los borre.
- **Swagger** solo se publica en el perfil `dev`.
