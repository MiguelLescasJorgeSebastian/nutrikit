/**
 * Módulo Acceso del paciente: enlaces privados sin registro, con PIN opcional.
 *
 * <p>Fase 1. Solo exponer en la raíz de este paquete la API pública del módulo;
 * el resto va en subpaquetes internos (domain, application, infrastructure, web).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Acceso del paciente")
package dev.jorgemiguel.nutrikit.patientaccess;
