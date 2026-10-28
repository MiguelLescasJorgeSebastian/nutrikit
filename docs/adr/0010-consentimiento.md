# 0010. Aviso de privacidad y consentimiento del paciente

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

Los datos de salud son sensibles (LFPDPPP, art. 3 fr. VI) y su tratamiento requiere consentimiento expreso (art. 9), con base en un aviso de privacidad (arts. 15 a 17). RNF-LEG-01 pide registrarlo, y además el sistema debe impedir el caso incorrecto: guardar datos de salud de alguien que no consintió.

## Decisión

### Aviso de privacidad por consultorio

Cada consultorio es el responsable de los datos de sus pacientes y publica su propio aviso. `aviso_de_privacidad` guarda cada versión con su texto completo. Una versión publicada **no cambia** (`@Immutable` y la app solo tiene `INSERT`), porque un consentimiento debe poder mostrar exactamente lo que se aceptó. La versión vigente es la última publicada. Solo la titular publica.

### Consentimiento como historial de hechos

`consentimiento` es *append-only*: cada fila es un hecho (`OTORGADO` o `RETIRADO`) con la versión del aviso, el medio (`PRESENCIAL`, `EN_LINEA`), la fecha y quién lo capturó. Nunca se edita ni se borra; la base lo garantiza porque la app solo tiene `SELECT` e `INSERT`. El estado actual es el último hecho del paciente.

Diferencias con el modelo de datos v0.1: `aceptado_en` pasa a `registrado_en` (sirve para ambas acciones), y se agregan `accion` y `registrado_por`. Una FK compuesta `(tenant_id, version_aviso)` garantiza que la versión existe en el aviso de ese consultorio.

### Dónde vive la regla

"No hay datos de salud sin consentimiento vigente" cruza dos agregados (paciente y su historia, consentimiento). Se valida en la capa de aplicación: `ConsentimientoService.exigirVigente`, en la misma transacción que la escritura. `HistoriaClinicaService.guardar` la llama antes de escribir, y los módulos futuros que guarden datos de salud (consultas, mediciones) harán lo mismo. Sin consentimiento: 409 *Consentimiento requerido*.

### Versiones nuevas del aviso

El cliente no elige la versión al otorgar: siempre es la vigente. Un consentimiento de una versión anterior **sigue vigente**, porque bloquear a todos los pacientes al publicar un cambio dejaría al consultorio sin operar. `GET /api/consentimientos/desactualizados` lista a quienes aceptaron una versión anterior, para pedirles que acepten la nueva. Si el cambio del aviso agregara finalidades que requieren consentimiento nuevo, esta regla debe endurecerse.

## Consecuencias

- **El retiro no borra datos:** detiene el tratamiento nuevo (ya no se guarda historia clínica), pero lo guardado se conserva, porque la NOM-004 obliga a conservar el expediente. Borrarlo o anonimizarlo será parte de los derechos ARCO (cancelación) y de su propio proceso.
- **Derechos ARCO pendientes:** acceso (exportar el expediente), rectificación (ya existe la edición), cancelación (anonimizar `paciente` y `historia_clinica` y destruir datos cifrados) y oposición. Este ADR no los cubre.
- **Concurrencia:** si un retiro y una escritura de historia ocurren al mismo tiempo, la escritura puede pasar la validación justo antes del retiro. La ventana es de milisegundos y la escritura queda auditada; si hiciera falta cerrarla, se bloquea la fila del último consentimiento (`SELECT ... FOR SHARE`).
- **Consentimiento en línea por el propio paciente:** el medio `EN_LINEA` ya existe y `registrado_por` acepta `null`, para cuando el paciente lo acepte desde su enlace privado (#26).
