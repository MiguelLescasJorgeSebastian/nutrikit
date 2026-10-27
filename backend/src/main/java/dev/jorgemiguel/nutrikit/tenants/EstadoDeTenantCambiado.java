package dev.jorgemiguel.nutrikit.tenants;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/**
 * Un consultorio cambió de estado. Los estados viajan como texto ({@code ACTIVO},
 * {@code SUSPENDIDO}, {@code CANCELADO}) para no exponer el enum interno del módulo.
 */
public record EstadoDeTenantCambiado(UUID tenantId, String estadoAnterior, String estadoNuevo, Origen origen,
		Instant ocurridoEn) {
}
