package dev.jorgemiguel.nutrikit.tenants;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/**
 * Se dio de alta un consultorio. Evento de la API pública: otros módulos lo escuchan sin conocer
 * la entidad {@code Tenant}.
 */
public record TenantCreado(UUID tenantId, String slug, Origen origen, Instant ocurridoEn) {
}
