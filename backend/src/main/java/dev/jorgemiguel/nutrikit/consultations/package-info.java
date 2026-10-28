/**
 * Módulo Consultas: consultas, mediciones por indicador y el catálogo de indicadores.
 *
 * <p>Módulo opcional (encendido por defecto, ver {@code ModuloDelConsultorio}). Guarda el
 * {@code paciente_id} sin llave foránea y pregunta al módulo de pacientes por su API pública
 * ({@code Pacientes}). Ver docs/adr/0011-consultas-y-mediciones.md.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Consultas")
package dev.jorgemiguel.nutrikit.consultations;
