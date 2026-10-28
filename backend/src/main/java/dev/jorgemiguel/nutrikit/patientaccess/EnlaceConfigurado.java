package dev.jorgemiguel.nutrikit.patientaccess;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se cambió el PIN o la caducidad del enlace de un paciente. */
public record EnlaceConfigurado(UUID tenantId, UUID pacienteId, UUID enlaceId, Origen origen, Instant ocurridoEn) {
}
