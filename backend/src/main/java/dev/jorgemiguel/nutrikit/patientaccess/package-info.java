/**
 * Módulo Acceso del paciente: el paciente ve su página entrando con un enlace privado, sin crear
 * cuenta (ADR-0003, ADR-0014).
 *
 * <p>Módulo opcional (encendido por defecto). Lee los datos del paciente y de sus consultas por las
 * APIs públicas de {@code patients} y {@code consultations}; nunca por sus clases internas.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Acceso del paciente")
package dev.jorgemiguel.nutrikit.patientaccess;
