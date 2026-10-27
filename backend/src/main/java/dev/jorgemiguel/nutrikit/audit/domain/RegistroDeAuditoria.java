package dev.jorgemiguel.nutrikit.audit.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/**
 * Una entrada de la bitácora: quién ({@code origen}) hizo qué ({@code accion}) sobre qué recurso
 * y cuándo. Inmutable, como la tabla.
 *
 * @param ocurridoEn cuándo pasó, no cuándo se escribió: el listener puede correr después
 */
public record RegistroDeAuditoria(UUID tenantId, Origen origen, String accion, String recursoTipo, UUID recursoId,
		Instant ocurridoEn) {

	public RegistroDeAuditoria {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(origen, "origen");
		Objects.requireNonNull(accion, "accion");
		Objects.requireNonNull(recursoTipo, "recursoTipo");
		Objects.requireNonNull(ocurridoEn, "ocurridoEn");
	}

}
