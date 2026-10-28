# 0012. Indicadores calculados: dominio puro, guardados y recalculados

- Estado: Aceptada
- Fecha: 2026-10-27

## Contexto

RF-CON-03 pide calcular IMC, índice cintura-cadera y gasto energético basal en cada consulta. Son fórmulas con valores de referencia publicados, sin base de datos ni HTTP de por medio, y el gasto energético tiene varias fórmulas aceptadas.

## Decisión

### Dominio puro en `consultations.domain.calculos`

Cada cálculo implementa `Calculo` (código del indicador y `Optional<BigDecimal> calcular(Entradas)`); si falta un dato devuelve vacío, nunca lanza. `CalculadoraDeIndicadores` aplica todos. Ninguna clase importa Spring, JPA ni Hibernate, y una regla de ArchUnit en las pruebas lo verifica. Por eso no viven en el controlador ni en el servicio: se prueban sin contexto de Spring y en milisegundos, se reutilizan (vista previa en el frontend, importación de InBody) y el servicio solo reúne datos.

| Indicador | Fórmula | Fuente | Redondeo |
| --- | --- | --- | --- |
| IMC | peso / talla² (talla en m) | OMS, TRS 894 (2000) | 2 decimales |
| Índice cintura-cadera | cintura / cadera | OMS, consulta de expertos 2008 | 2 decimales |
| Gasto energético basal | Mifflin-St Jeor (por defecto) | Mifflin et al., Am J Clin Nutr 1990 | kcal enteras |
| | Harris-Benedict revisada | Roza y Shizgal, Am J Clin Nutr 1984 | kcal enteras |

El gasto energético usa el patrón **Strategy**: `FormulaGastoEnergetico` con dos implementaciones, elegida por consultorio en `configuracion_de_calculos` (`GET`/`PUT /api/configuracion/gasto-energetico`, cambiarla solo la titular). La edad se calcula a la fecha de la consulta, no a hoy. Sexo y fecha de nacimiento llegan por la API pública `Pacientes.perfilClinico`.

Todo con `BigDecimal` y `RoundingMode.HALF_UP`.

### Guardar el valor calculado

Los derivados se guardan como mediciones con origen `CALCULADO`, junto a las capturadas. No se calculan al consultar porque:

- la consulta muestra lo que se calculó con la fórmula vigente ese día; si el consultorio cambia de fórmula, las consultas pasadas no cambian en silencio;
- las series para gráficas (#25) leen una sola tabla con un índice, sin recalcular miles de valores;
- quedan en la bitácora y en el expediente igual que el resto.

El costo es mantenerlos al día, y por eso hay un único punto que los escribe: `DerivadosDeConsulta.recalcular`, que se llama al registrar y al corregir.

### Reglas

- **Un indicador calculado no se captura ni se corrige a mano** (400). Los tres son globales con `calculado = true`.
- **Corregir una entrada recalcula sus derivados:** `PUT /api/consultas/{id}/mediciones/{indicador}`. La app solo tiene permiso de `UPDATE` sobre la columna `valor` de `medicion`; `@DynamicUpdate` hace que Hibernate actualice solo esa columna. La corrección se audita (`MEDICION_CORREGIDA`).
- Los derivados no se validan contra un rango de captura: salen de valores ya validados.

### TDD

Las fórmulas se escribieron prueba primero, en dos ciclos rojo → verde y un refactor. Los commits del PR muestran cada paso.

## Consecuencias

- **Cambiar de fórmula** no recalcula consultas pasadas: solo aplica a las que se registren o corrijan después.
- **Corregir la fecha de nacimiento del paciente** no recalcula el gasto energético de consultas ya guardadas. Si hiciera falta, un listener de `PacienteActualizado` podría recalcularlas.
- **El IMC en menores de edad** se interpreta con percentiles por edad y sexo, no con los puntos de corte de adultos; la categoría de la OMS que se implementó es para adultos.
- **Serialización de decimales:** `stripTrailingZeros()` convierte 80 en `8E+1`; `Indicador.sinCerosSobrantes` lo evita, y una prueba de regresión lo cubre.
