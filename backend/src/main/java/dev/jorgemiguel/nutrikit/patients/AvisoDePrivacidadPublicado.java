package dev.jorgemiguel.nutrikit.patients;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** El consultorio publicó una versión nueva de su aviso de privacidad. */
public record AvisoDePrivacidadPublicado(UUID tenantId, UUID avisoId, String version, Origen origen,
		Instant ocurridoEn) {
}
