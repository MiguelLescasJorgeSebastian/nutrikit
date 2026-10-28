# 0007. Módulos encendidos y apagados por consultorio

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

El producto es un núcleo con módulos opcionales que cada consultorio enciende según lo que contrata (RF-TEN-05, ADR-0001). Un módulo apagado debe comportarse como si no existiera: sin endpoints ni datos nuevos, y sin aparecer en el frontend. Es un *entitlement* por cliente, no un flag de lanzamiento: dura lo que dure el contrato, no unas semanas.

## Decisión

### Catálogo

`tenants.ModuloDelConsultorio` es la única lista de módulos. El código de cada uno es el nombre de su paquete en Spring Modulith:

| Código | Núcleo | Por defecto |
| --- | --- | --- |
| `patients` | Sí | Encendido (siempre) |
| `consultations` | No | Encendido |
| `patientaccess` | No | Encendido |
| `mealplans` | No | Apagado |
| `integrations` | No | Apagado |
| `scheduling` | No | Apagado |
| `website` | No | Apagado |

Los módulos de plataforma (`tenants`, `iam`, `audit`, `shared`) no son módulos del consultorio. `tenant_modulo.modulo` tiene un `CHECK` con los mismos códigos, y una prueba falla si Java y la base no coinciden.

### A qué módulo pertenece un endpoint

Se deduce del paquete del controlador (`dev.jorgemiguel.nutrikit.<codigo>...`), no de una anotación. Un controlador nuevo queda protegido sin que nadie tenga que recordar marcarlo.

### Cómo se bloquea

Un `HandlerInterceptor`, porque es el primer punto donde ya se sabe qué controlador atenderá la petición (un filtro de servlet todavía no lo sabe). Si el módulo está apagado para el consultorio de la petición, lanza `NoResourceFoundException`, la misma excepción que Spring MVC usa para una ruta inexistente: la respuesta es un 404 idéntico y no revela que el módulo existe ni que el consultorio no lo tiene contratado.

Solo aplica a peticiones de un consultorio. Las rutas del operador y los módulos de núcleo nunca se bloquean.

### Registro y cambios

- **Alta de consultorio:** un listener de `TenantCreado`, después del commit y en el mismo hilo, registra todos los módulos con su valor por defecto. Los consultorios anteriores los recibieron en la migración `V6`.
- **Cambios:** `PUT /api/operador/tenants/{id}/modulos/{codigo}` con `{"habilitado": true|false}`. Es idempotente: repetir el mismo valor no cambia nada ni publica evento. Apagar un módulo de núcleo responde 409.
- **Auditoría:** cada cambio publica `ModuloDeConsultorioCambiado` y queda en la bitácora como `MODULO_ENCENDIDO` o `MODULO_APAGADO`, con el módulo en el tipo de recurso (`MODULO_SCHEDULING`).

### Caché

El estado se consulta en cada petición, así que se guarda en Caffeine por consultorio (máximo 60 segundos). Cada cambio invalida la entrada **después del commit**: si se invalidara antes, una petición concurrente podría leer el valor viejo y volver a guardarlo.

## Consecuencias

- **Datos de un módulo apagado:** se conservan. Apagar no borra nada; al encenderlo de nuevo, todo sigue ahí. Borrar sería irreversible y el expediente clínico debe conservarse por norma.
- **Varias instancias:** la invalidación solo es local. Las demás instancias ven el cambio cuando vence su entrada (hasta 60 segundos). Si eso no basta, se puede publicar la invalidación por un canal compartido (Redis pub/sub) o externalizar el evento.
- **Código en otros módulos:** este mecanismo protege los endpoints HTTP. Los listeners y las tareas programadas de un módulo opcional deben revisar si el módulo está encendido antes de actuar para un consultorio.
- **Agregar un módulo:** agrégalo al enum, al `CHECK` en una migración nueva y a los consultorios existentes en esa misma migración.
