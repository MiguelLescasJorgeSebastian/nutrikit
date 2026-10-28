package dev.jorgemiguel.nutrikit.patientaccess;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** El paciente abrió su página con su enlace privado. */
public record AccesoPorEnlace(UUID tenantId, UUID pacienteId, UUID enlaceId, Origen origen, Instant ocurridoEn) {
}
