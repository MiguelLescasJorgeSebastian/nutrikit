# 0011. Consultas y mediciones con catálogo de indicadores

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

En cada consulta se miden cosas distintas según la nutrióloga y el equipo: peso y talla siempre, pero también perímetros, pliegues o lo que reporta una báscula de bioimpedancia. RF-MED-02 pide que la nutrióloga agregue indicadores sin cambiar el esquema, y RF-MED-03, saber de dónde salió cada valor.

## Decisión

### Filas con catálogo, no una columna por medida

`medicion (consulta_id, indicador_id, valor, origen)` con un catálogo `indicador`. Es un EAV acotado: el "atributo" es una fila de un catálogo tipado (nombre, unidad, rango), no texto libre, y el valor siempre es numérico.

| | Columna por medida | Filas con catálogo |
| --- | --- | --- |
| Agregar una medida | Migración | Una fila en `indicador` |
| Validar tipo y rango | `CHECK` por columna | Rango en el catálogo, validado por la app |
| Leer una consulta completa | Una fila | Varias filas (una consulta SQL con join) |
| Graficar un indicador en el tiempo | Una columna | `WHERE indicador_id = ?` con índice `(tenant_id, indicador_id)` |

Lo que se pierde (tipos y restricciones por columna, consultas "anchas" simples) se compensa con el rango por indicador, `numeric(10,3)` para todos los valores, `UNIQUE (consulta_id, indicador_id)` y vistas o series precalculadas cuando haga falta (#25).

### Catálogo global y por consultorio

`indicador.tenant_id` nulo = global (sembrado por migración: peso, talla, cintura, cadera, % grasa, % músculo, grasa visceral, agua corporal). Con `tenant_id` = propio del consultorio.

- **RLS:** dos políticas. Para leer, `tenant_id IS NULL OR tenant_id = actual`. Para insertar, solo `tenant_id = actual`: un consultorio no puede crear globales.
- **Hibernate:** un segundo filtro, `tenantOGlobal`, con la misma condición.
- **Códigos:** únicos entre los globales y dentro de cada consultorio (índices únicos parciales). Que un código propio no repita uno global lo valida la aplicación.
- **Rango razonable:** `valor_minimo` y `valor_maximo` no estaban en el modelo de datos v0.1. Rechazan errores de captura (un peso de 5000 kg).

### Agregado

`Consulta` es la raíz y contiene sus `Medicion`: se crean juntas y "un indicador una sola vez por consulta" es una regla del agregado. `Indicador` es otro agregado, referenciado solo por id. La medición guarda su `origen` (`MANUAL`, `INBODY_CSV`, `CALCULADO`) y, si viene de una importación, su `importacion_id`.

### Referencia al paciente entre módulos

`consulta.paciente_id` no tiene FK (ADR-0001): `consultations` se puede apagar o extraer sin tocar `paciente`. Para validar, usa la API pública `patients.Pacientes` (`estaActivo`, `tieneConsentimientoVigente`), nunca sus repositorios. `ModularityTests` lo verifica. Las mediciones son datos de salud, así que también exigen consentimiento vigente (ADR-0010).

### Otros

- **Valores:** `BigDecimal` en Java y `numeric(10,3)` en la base. `double` no representa exactamente 72.3; en datos clínicos eso se acumula y confunde.
- **Notas:** cifradas con `CampoCifrado` (ADR-0009).
- **Línea de tiempo:** orden por `fecha DESC, id DESC`; las mediciones de una página se cargan juntas (`@BatchSize`).
- **Inmutabilidad:** sin `UPDATE` ni `DELETE` para la app sobre consultas, mediciones e indicadores; corregir una consulta queda pendiente y deberá dejar rastro.

## Consecuencias

- **Paciente dado de baja:** sus consultas se conservan (expediente, NOM-004), pero su línea de tiempo responde 404 como el paciente. Si se reactiva o se exporta su expediente (ARCO), siguen ahí.
- **Validar existencia sin FK** deja una ventana: un paciente dado de baja justo después de la validación podría recibir una consulta. Se acepta por el desacoplamiento; un listener de `PacienteDadoDeBaja` podría marcar o revisar esos casos.
- **Indicadores propios:** no se editan ni se borran todavía. Cambiar el rango de un indicador con mediciones guardadas requiere decidir qué pasa con los valores fuera del nuevo rango.
