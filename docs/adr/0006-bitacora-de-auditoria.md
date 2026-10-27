# 0006. Bitácora de auditoría con eventos de dominio

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

Los expedientes clínicos exigen trazabilidad (RNF-SEG-03, NOM-004): quién hizo qué, sobre qué recurso, cuándo y desde dónde, sin que nadie pueda alterar el registro. Además es la primera comunicación entre módulos por eventos, el patrón central de la arquitectura (ADR-0001).

## Decisión

### Eventos en la API pública de quien publica

`tenants` publica `TenantCreado` y `EstadoDeTenantCambiado`; `iam` publica `InicioDeSesionExitoso` e `InicioDeSesionFallido`. Viven en la raíz del paquete del módulo que los publica, porque describen algo que le pasó a ese módulo. `audit` los escucha sin conocer ninguna clase interna, y ningún módulo llama a `audit`: se podría apagar o extraer sin tocar a los demás. `ModularityTests` lo verifica.

Cada evento lleva el `tenantId` y un `Origen` (tipo y id del actor, IP y user agent). El origen se captura al publicar, en el hilo de la petición, porque el listener corre después en otro hilo, sin petición ni usuario autenticado.

### Entrega: después del commit, en otro hilo, con outbox

- `@TransactionalEventListener` (fase `AFTER_COMMIT`): no se audita algo que terminó en rollback, como un alta que falló después de publicar el evento.
- `@Async`: la petición no espera a la bitácora.
- **Registro de publicaciones de Spring Modulith (outbox):** cada evento se guarda en `event_publication` en la misma transacción que lo publica, y se marca completo solo si el listener termina bien. Si falla, queda pendiente y se reenvía al arrancar (`republish-outstanding-events-on-restart`) y cada 5 minutos si lleva más de 5 minutos pendiente.

No se usa `@ApplicationModuleListener` porque abre la transacción antes de entrar al método, y aquí primero hay que entrar al consultorio del evento (`TenantContext.ejecutarComo`) para que la transacción fije `app.tenant_id` y RLS acepte el `INSERT`.

El inicio de sesión fallido usa `noRollbackFor`: sin commit, el evento nunca se entregaría.

### Tabla `auditoria`

- **Append-only:** la app solo tiene `INSERT` y `SELECT` sobre la tabla padre y ningún permiso sobre las particiones. Ni un error de programación puede editar o borrar registros.
- **RLS** por `tenant_id`, como toda tabla de negocio. Sin FK a `tenant`: la bitácora se conserva aunque se borre el consultorio.
- **Particionada por mes** (`PARTITION BY RANGE (ocurrido_en)`): borrar o archivar un mes es quitar una partición, y las consultas por fecha solo leen las particiones necesarias.
- **Creación de particiones:** es DDL y la app no tiene ese permiso. La función `asegurar_particion_auditoria(date)` corre como su dueño (`SECURITY DEFINER`, con `search_path` fijo) y solo crea la partición de un mes. La app la llama al arrancar y a diario para el mes actual y los dos siguientes.
- **Sin partición `DEFAULT`:** si faltara la del mes, el `INSERT` falla, el evento queda pendiente y se reintenta cuando exista. Una `DEFAULT` aceptaría esas filas y después impediría crear la partición de ese mes.
- Se escribe con SQL (`JdbcClient`) y no con JPA: no hay entidad que cargar ni modificar, y `ip` es `inet`.

### Qué se registra

| Acción | Actor | Recurso |
| --- | --- | --- |
| `TENANT_CREADO` | Operador | El consultorio |
| `TENANT_ESTADO_CAMBIADO` | Operador | El consultorio |
| `LOGIN_EXITOSO` | El usuario | El usuario |
| `LOGIN_FALLIDO` | Anónimo | La cuenta atacada, si el correo existe |

La respuesta de un login fallido no dice si el correo existe (ADR-0005), pero la bitácora sí guarda la cuenta, para detectar ataques contra una persona.

## Consecuencias

- **Consistencia eventual:** el registro aparece milisegundos después de la respuesta. Las pruebas esperan con `Scenario` o Awaitility.
- **Entrega al menos una vez:** un reintento después de una falla a medias podría duplicar un registro. Para auditoría es preferible a perderlo.
- **Cambio de servicio:** si `audit` se separa, los mismos eventos se publican en Kafka u otro broker desde `event_publication` (Spring Modulith tiene externalización de eventos), sin tocar a quien publica.
- **Fuera de alcance:** el inicio de sesión del operador no se audita porque no pertenece a ningún consultorio y `tenant_id` es obligatorio. Para auditar acciones de plataforma hará falta una bitácora propia o un `tenant_id` nulo con otra política.
- **Retención:** falta decidir cuánto tiempo se conservan las particiones (NOM-004 pide al menos 5 años para el expediente).
