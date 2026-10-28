package dev.jorgemiguel.nutrikit.patientaccess;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se revocó el enlace privado de un paciente. */
public record EnlaceRevocado(UUID tenantId, UUID pacienteId, UUID enlaceId, Origen origen, Instant ocurridoEn) {
}
