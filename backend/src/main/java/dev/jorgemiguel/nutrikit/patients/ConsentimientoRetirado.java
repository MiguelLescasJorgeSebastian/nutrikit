package dev.jorgemiguel.nutrikit.patients;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** El paciente retiró su consentimiento; el otorgamiento anterior se conserva en el historial. */
public record ConsentimientoRetirado(UUID tenantId, UUID pacienteId, UUID consentimientoId, String versionAviso,
		Origen origen, Instant ocurridoEn) {
}
