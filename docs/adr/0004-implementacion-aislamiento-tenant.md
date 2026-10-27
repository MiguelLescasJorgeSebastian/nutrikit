# 0004. Implementación del aislamiento entre consultorios

- Estado: Aceptada
- Fecha: 2026-10-27
- Detalla: [0002](0002-multi-tenant.md)

## Contexto

El ADR 0002 decidió una base compartida con `tenant_id` y dos barreras: filtro en la aplicación y Row-Level Security (RLS). Faltaba decidir cómo se identifica el consultorio, dónde vive durante la petición, cómo llega a Postgres y con qué usuario se conecta la app.

## Decisión

### 1. Identificación del consultorio

Toda petición a `/api/**` pertenece a un consultorio, salvo `/api/operador/**` (plataforma). Un filtro de servlet lo resuelve en este orden:

1. Cabecera `X-Tenant` con el slug, **solo si** `nutrikit.tenancy.cabecera-habilitada=true` (perfil `dev`). En producción está apagada porque cualquiera podría enviarla.
2. Subdominio: `nutri-ana.nutrikit.mx` → `nutri-ana`. Un solo nivel; `www`, `api`, `app` y `admin` están reservados.

Sin consultorio, la respuesta es 400. Si el consultorio no existe o no está activo, la respuesta es 404, la misma en ambos casos para no revelar cuáles existen. Con la autenticación (#8), el consultorio del JWT deberá coincidir con el resuelto.

### 2. Contexto durante la petición

`TenantContext` guarda el id en un `ScopedValue` de Java 25, no en un `ThreadLocal`. El valor existe solo dentro de `TenantContext.ejecutarComo(...)` y desaparece al salir, aun con excepciones. No hay limpieza manual que olvidar, y un hilo reutilizado (de plataforma o virtual) no arrastra el consultorio anterior.

Los hilos nuevos no heredan el valor. Un listener asíncrono de eventos (#9) debe recibir el `tenantId` en el evento y volver a llamar a `ejecutarComo`.

### 3. Barrera 1: filtro de Hibernate

Las entidades con `tenant_id` llevan `@Filter(name = FiltroDeTenant.NOMBRE)`. El filtro está siempre activo (`autoEnabled`), también aplica a `findById` (`applyToLoadByKey`) y toma el valor del contexto. Sin contexto compara contra un UUID inexistente: no devuelve nada.

Se eligió `@Filter` sobre `@TenantId` porque `tenant_modulo` tiene `tenant_id` dentro de su llave compuesta, y porque `@TenantId` exige un consultorio para abrir cualquier sesión, incluidas las del operador. El `tenant_id` de los registros nuevos se toma siempre del contexto en la fábrica de la entidad, nunca de la entrada.

### 4. Barrera 2: Row-Level Security

Cada tabla con `tenant_id` activa RLS (`ENABLE` y `FORCE`) con la política:

```sql
USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
```

Un `JpaTransactionManager` propio ejecuta `set_config('app.tenant_id', <id>, true)` al iniciar cada transacción. El tercer argumento lo hace **local a la transacción**: Postgres lo borra en el commit o el rollback, así que una conexión que vuelve al pool de HikariCP nunca conserva el consultorio anterior. Fijarlo a nivel sesión obligaría a limpiarlo a mano al devolver la conexión, y un fallo dejaría datos expuestos a la siguiente petición.

`NULLIF` es necesario porque, tras usarse en una sesión, la variable vale `''` y no `NULL`, y `''::uuid` falla.

La tabla `tenant` no lleva RLS: el operador lista todos los consultorios y la resolución del consultorio la consulta antes de conocer el tenant.

### 5. Dos usuarios de base de datos

| Usuario | Uso | Permisos |
| --- | --- | --- |
| Dueño (`nutrikit` en desarrollo) | Solo Flyway | Crea y altera tablas |
| `nutrikit_app` | La aplicación | `SELECT`, `INSERT`, `UPDATE`, `DELETE` otorgados tabla por tabla. No es dueño ni superusuario, y no tiene `BYPASSRLS` |

El dueño de una tabla y los superusuarios se saltan RLS, así que la app nunca debe conectarse como ellos. Por eso la tabla de eventos de Spring Modulith ahora la crea Flyway (`V2`) y no la app al arrancar.

El rol se crea fuera de las migraciones porque es global del servidor y lleva contraseña: con `docker/postgres/init/01-rol-app.sql` en desarrollo y pruebas, y con la infraestructura en producción.

## Consecuencias

- **Migraciones nuevas:** cada tabla otorga sus permisos a `${app_user}` de forma explícita; si tiene `tenant_id`, activa RLS con la misma política y su entidad lleva el filtro.
- **Pruebas:** Testcontainers usa los mismos dos usuarios. `AislamientoTenantTest` prueba cada barrera por separado, la escritura cruzada, que la variable no sobrevive a la transacción y que la app no es dueña de las tablas.
- **Consultas fuera de transacción:** no tienen consultorio en la base y RLS no devuelve filas. Es un fallo cerrado: hay que abrir transacción para leer datos de un consultorio.
- **Operaciones del operador sobre datos de un consultorio** (por ejemplo, encender módulos): deben ejecutarse con `ejecutarComo(tenantId)` explícito, nunca saltándose RLS.
- **Costo:** la resolución consulta `tenant` en cada petición. Si se vuelve un cuello de botella, se puede cachear el mapa slug → id con invalidación al cambiar de estado.
- **Desarrollo:** una base creada antes de este cambio no tiene el rol `nutrikit_app`; se recrea con `docker compose down -v`.
