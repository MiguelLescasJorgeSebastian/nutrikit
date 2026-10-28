# 0014. Implementación del enlace privado del paciente

- Estado: Aceptada
- Fecha: 2026-10-27
- Detalla: [0003](0003-acceso-paciente-enlace.md)

## Contexto

ADR-0003 decidió que el paciente entra a su página con un enlace privado, sin cuenta. Este ADR registra cómo se implementó: el enlace es una credencial con el mismo cuidado que el inicio de sesión del personal (ADR-0005).

## Decisión

### El token

- **Opaco, no JWT:** 256 bits de `SecureRandom` en Base64 URL-safe (43 caracteres). Un JWT no sirve aquí: se tendría que poder revocar al instante (un JWT es válido hasta que caduca) y no hay nada que transportar en él.
- **En la base, solo su SHA-256** (`token_hash`). Un hash rápido basta porque el token tiene 256 bits de entropía y no hay diccionario que probar. Si la base se filtra, los enlaces no se pueden reconstruir.
- **Búsqueda por hash** con índice único. El token del atacante se convierte en hash antes de tocar la base, así que una comparación de tiempo no revela nada del token real.
- **En la ruta** (`/api/acceso/{token}`) y no en un parámetro de consulta, que más herramientas registran (proxies, analítica). La ruta también llega a los logs de acceso del servidor, así que esos logs deben tratarse como sensibles.

### El PIN

Opcional, de 4 a 8 dígitos, con **Argon2id** (`Argon2PasswordEncoder`, BouncyCastle). Un PIN de 4 dígitos son 10,000 combinaciones: su hash debe ser caro de calcular. Viaja en la cabecera `X-Pin`, nunca en la URL.

### Aislamiento

El consultorio sale del subdominio y la búsqueda del token ocurre dentro de su contexto, con RLS. Un token de un consultorio usado en otro no se encuentra (404). El enlace solo da acceso a su paciente: el servicio no recibe ningún id del cliente.

### Ciclo de vida

| Situación | Respuesta |
| --- | --- |
| Token inexistente o revocado | 404, el mismo mensaje en ambos casos |
| Caducado | 410 |
| Sin PIN o PIN incorrecto | 401 |
| 5 PIN incorrectos por enlace, o 20 fallos por IP, en 15 minutos | 429 con `Retry-After`, aunque el siguiente PIN sea correcto |
| Paciente dado de baja | 404 |

Un solo enlace vigente por paciente (índice único parcial). Regenerar revoca el anterior en la misma transacción y, antes de insertar el nuevo, lo escribe con `saveAndFlush`, porque Hibernate ejecuta los `INSERT` antes que los `UPDATE`. El token solo se devuelve al generarlo; después solo se puede consultar el estado.

### Segunda cadena de seguridad

`/api/acceso/**` tiene su propio `SecurityFilterChain` con `@Order(1)`: sin JWT, sin sesión, y con las cabeceras de una respuesta con datos de salud: `Cache-Control: no-store`, `Referrer-Policy: no-referrer` (el token va en la URL y no debe viajar en el `Referer` si la página enlaza a otro sitio) y `X-Robots-Tag: noindex, nofollow`. El resto de `/api/**` sigue en la cadena del personal con JWT.

### Qué ve el paciente

Su nombre, su última consulta (mediciones, sin notas) y la serie de peso. Todo llega por APIs públicas: `patients.Pacientes.datosGenerales` y la nueva `consultations.ConsultasDelPaciente`. La visibilidad por indicador (`visibilidad_indicador`) llega en la fase 2.

### Bitácora

Cada acceso exitoso (`ACCESO_PACIENTE`, con el paciente como actor nuevo `PACIENTE`) y cada PIN rechazado (`ACCESO_PACIENTE_RECHAZADO`) quedan con IP y user agent, igual que generar, configurar y revocar el enlace.

### Límite de intentos compartido

El limitador de ventana deslizante pasó de `iam` a `shared`: cada módulo crea su propia instancia (inicio de sesión y enlaces), así los conteos no se mezclan. El 429 lo responde el manejador global.

## Consecuencias

- **Quien tenga el enlace ve la página** mientras no esté revocado ni caducado; el PIN y la caducidad lo mitigan cuando el consultorio los activa.
- **Si el paciente pudiera escribir datos**, el enlace ya no bastaría: haría falta protección CSRF (con cookies), un segundo factor más fuerte que un PIN y operaciones idempotentes y auditadas por campo.
- **Módulo apagado:** si el consultorio apaga `patientaccess`, tanto la página como la gestión del enlace responden 404.
- **Pendiente:** "próximas citas" depende de la agenda (fase 3), y la conversión del enlace en cuenta (ADR-0003) no está implementada.
