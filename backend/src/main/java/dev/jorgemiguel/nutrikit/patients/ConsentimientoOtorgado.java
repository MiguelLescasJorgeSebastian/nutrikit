package dev.jorgemiguel.nutrikit.patients;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** El paciente aceptó una versión del aviso de privacidad. */
public record ConsentimientoOtorgado(UUID tenantId, UUID pacienteId, UUID consentimientoId, String versionAviso,
		Origen origen, Instant ocurridoEn) {
}
