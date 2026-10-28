package dev.jorgemiguel.nutrikit.patientaccess;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se generó (o regeneró) el enlace privado de un paciente; el anterior quedó revocado. */
public record EnlaceGenerado(UUID tenantId, UUID pacienteId, UUID enlaceId, Origen origen, Instant ocurridoEn) {
}
