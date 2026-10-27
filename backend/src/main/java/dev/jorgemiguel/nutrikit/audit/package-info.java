/**
 * Módulo Auditoría: bitácora append-only de quién hizo qué, sobre qué recurso y cuándo.
 *
 * <p>No expone API: escucha eventos de dominio de otros módulos (alta y cambio de estado de
 * consultorios, inicios de sesión) y los guarda en {@code auditoria}. Ningún módulo lo llama
 * directamente. Ver docs/adr/0006-bitacora-de-auditoria.md.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Auditoría")
package dev.jorgemiguel.nutrikit.audit;
