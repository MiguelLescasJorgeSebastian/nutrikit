# 0008. Pacientes: baja lógica, búsqueda y ediciones concurrentes

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

El paciente es la raíz del expediente (RF-PAC-01, RF-PAC-02, RF-PAC-04). Su expediente debe conservarse aunque deje de atenderse (NOM-004). La búsqueda se hace por nombre o teléfono, como lo escribe la recepción: sin acentos y en cualquier combinación de mayúsculas. Dos personas del consultorio pueden editar al mismo paciente a la vez.

## Decisión

### Baja lógica

`eliminado_en` marca la baja. `@SQLRestriction("eliminado_en IS NULL")` en la entidad agrega la condición a toda consulta JPA (listados, búsquedas y `findById`), así ningún código nuevo puede olvidarla. La app no tiene permiso de `DELETE` sobre `paciente`, y la FK a `tenant` no es en cascada: la base garantiza que un expediente no se borra por error.

### Búsqueda

- Columna generada `texto_busqueda = lower(inmutable_unaccent(nombre || ' ' || apellidos || ' ' || telefono))`: "José Pérez" → "jose perez".
- `inmutable_unaccent` envuelve `unaccent` con el diccionario fijo, porque `unaccent` no es `IMMUTABLE` y no se puede usar en columnas generadas ni índices.
- Índice GIN trigram (`pg_trgm`) que empieza por `tenant_id` (`btree_gin`) y solo cubre pacientes activos.
- Cada palabra buscada debe aparecer: `texto_busqueda LIKE '%termino%'`. El término se normaliza en la base con la misma función que la columna, así Java y Postgres no pueden diferir en qué es un acento. `%` y `_` se escapan.

**Trigram y no B-tree:** un B-tree solo sirve para prefijos (`LIKE 'jose%'`); "perez" en medio del nombre requiere buscar subcadenas, y eso lo resuelve el índice de trigramas. **Trigram y no full-text:** full-text busca palabras completas con raíces lingüísticas; para nombres y teléfonos parciales ("per", "1234") conviene la coincidencia por subcadena.

### Ediciones concurrentes

Bloqueo optimista con `@Version`. El cliente envía la versión que leyó:

1. Si ya no es la actual, 409 con `versionActual`: alguien editó mientras tanto.
2. Si dos ediciones llegan casi al mismo tiempo con la misma versión, ambas pasan la comparación, pero el `UPDATE ... WHERE version = ?` de la segunda no afecta filas y Hibernate lanza `ObjectOptimisticLockingFailureException`, que también se responde con 409.

### Otros datos

- **Edad:** se calcula al responder a partir de `fecha_nacimiento`; guardarla la dejaría desactualizada.
- **Obligatorios:** nombre, apellidos, fecha de nacimiento y sexo biológico (lo usan las fórmulas clínicas). Teléfono, correo y ocupación son opcionales.
- **Teléfono:** se normaliza a dígitos (`(55) 1234-5678` → `5512345678`).
- **Correo:** se guarda en minúsculas (`citext`).
- **Roles:** dar de baja requiere `TITULAR` o `COLABORADOR` (`@PreAuthorize`); una asistente puede registrar y editar datos generales.
- **Eventos:** `PacienteCreado`, `PacienteActualizado` y `PacienteDadoDeBaja`, solo con identificadores, se auditan.

## Consecuencias

- **Derecho de cancelación (LFPDPPP):** la baja lógica conserva datos personales. Cuando un paciente pida cancelar sus datos, hará falta un proceso aparte: anonimizar los datos de identificación y conservar solo lo que la NOM-004 obliga a guardar, durante el plazo que exige.
- **Búsquedas de una o dos letras:** no aprovechan el índice trigram (necesita 3 caracteres) y recorren los pacientes del consultorio; con miles de pacientes por consultorio sigue siendo rápido.
- **Edad y zona horaria:** la fecha de hoy se toma en UTC. Cuando exista la zona del consultorio, la edad debe calcularse con ella.
- **Migración:** el issue pedía `V6`, pero ya la ocupaba #19; esta es `V7`.
