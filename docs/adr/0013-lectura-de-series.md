# 0013. Series de indicadores: lectura con SQL y sin entidades

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

La gráfica de evolución (RF-CON-04) pide, para un paciente, los valores de uno o varios indicadores a lo largo de años de consultas. La comparación entre dos consultas (RF-CON-05) lee las mismas tablas. Es la primera consulta pensada para rendimiento: con 5 años de consultas semanales debe responder en menos de 300 ms.

## Decisión

### Lectura separada de la escritura (CQRS ligero)

Las escrituras pasan por el agregado `Consulta` porque ahí viven las reglas. Las lecturas de reporte no: `SeriesDeIndicadores` hace **una sola consulta SQL** (`JdbcClient`) que proyecta a records exactamente las columnas que necesita la gráfica (código, nombre, unidad, si es calculado, fecha, valor, origen).

Cargar entidades significaría hidratar 260 consultas con sus colecciones de mediciones, descifrar notas que nadie va a leer, llenar el contexto de persistencia y arriesgar N+1. No hay nada que validar ni modificar en una lectura, así que el modelo de dominio no aporta.

El SQL nativo no pasa por el filtro de Hibernate, así que filtra `tenant_id` de forma explícita; RLS sigue aplicando como segunda barrera.

### Índices

La consulta recorre `consulta` por `(tenant_id, paciente_id, fecha)` y, por cada consulta, busca la medición por `(consulta_id, indicador_id)`. Los dos índices ya existían desde V10 (`consulta_tenant_paciente_fecha_idx` y la restricción `UNIQUE (consulta_id, indicador_id)`), así que **no se agrega ninguno**. Medido con 37,761 consultas y 302,088 mediciones en el consultorio (5 años de consultas semanales de un paciente más 1,500 pacientes de ruido), pidiendo peso e IMC (520 puntos):

| Escenario | Acceso a `consulta` | Buffers | Ejecución |
| --- | --- | --- | --- |
| Sin `(tenant_id, paciente_id, fecha)` | Parallel Seq Scan, descarta 37,500 filas | 3,765 | 3.3 ms |
| **Con el índice** | Index Only Scan, 260 filas | 2,092 | **1.2 ms** |
| Índice cubriente extra en `medicion` `(consulta_id, indicador_id) INCLUDE (tenant_id, valor, origen)` | Igual | 2,093 | 3.3 ms |

**Orden de las columnas:** primero `tenant_id` (toda consulta de negocio filtra por consultorio y RLS lo exige), después `paciente_id` (igualdad) y al final `fecha`, que se usa como rango y para ordenar. Las columnas de igualdad van antes que la de rango: así el índice lleva directo al tramo del paciente, ya ordenado por fecha.

**Por qué no el índice cubriente:** no mejora (las 520 lecturas de la tabla siguen ahí hasta que `VACUUM` actualiza el *visibility map*) y encarece cada escritura de mediciones. Sin el índice de `consulta`, el costo crece con todas las consultas del consultorio; con él, solo con las del paciente.

### Contrato

- `GET /api/pacientes/{id}/series?indicadores=peso,imc&desde=&hasta=`: una serie por indicador con sus puntos `{fecha, consultaId, valor, origen}` en orden ascendente, que es el formato que espera una librería de gráficas (eje X = fecha). Sin `indicadores`, todos los que tengan datos; un indicador pedido sin datos devuelve una serie vacía con su unidad. Máximo 10 indicadores; las fechas son inclusivas.
- `GET /api/consultas/{a}/comparacion/{b}`: por cada indicador presente en alguna de las dos consultas, valor anterior, valor posterior, diferencia (posterior − anterior) y porcentaje respecto del anterior. El orden de los ids no importa. Si un valor falta, o el anterior es cero, la diferencia es `null`.
- Ambos con ejemplos en OpenAPI.

## Consecuencias

- **Sin N+1:** una prueba cuenta las sentencias SQL y verifica que el número es el mismo con 10 que con 260 consultas, y con 1 que con 3 indicadores.
- **Dos modelos que mantener:** si cambia el esquema de mediciones, hay que revisar también el SQL de lectura. Las pruebas de punta a punta lo cubren.
- **Si el volumen creciera mucho** (decenas de años o miles de puntos por gráfica), el siguiente paso sería agregar en la base (promedio semanal o mensual) o una vista materializada, no más índices.
