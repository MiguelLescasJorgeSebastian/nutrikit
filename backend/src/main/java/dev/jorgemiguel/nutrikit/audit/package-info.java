/**
 * Módulo Auditoría: bitácora append-only de accesos y cambios a expedientes.
 *
 * <p>Fase 0. Solo exponer en la raíz de este paquete la API pública del módulo;
 * el resto va en subpaquetes internos (domain, application, infrastructure, web).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Auditoría")
package dev.jorgemiguel.nutrikit.audit;
