package dev.jorgemiguel.nutrikit.patientaccess;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Alguien usó un enlace válido con un PIN incorrecto o sin PIN. */
public record AccesoPorEnlaceRechazado(UUID tenantId, UUID pacienteId, UUID enlaceId, Origen origen, Instant ocurridoEn) {
}
