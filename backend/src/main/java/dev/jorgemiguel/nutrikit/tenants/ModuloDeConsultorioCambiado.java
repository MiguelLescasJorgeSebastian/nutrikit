package dev.jorgemiguel.nutrikit.tenants;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se encendió o apagó un módulo de un consultorio. */
public record ModuloDeConsultorioCambiado(UUID tenantId, ModuloDelConsultorio modulo, boolean habilitado,
		Origen origen, Instant ocurridoEn) {
}
