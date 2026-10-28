package dev.jorgemiguel.nutrikit.patients;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se editaron los datos generales de un paciente. Solo lleva identificadores: los datos personales no viajan en eventos. */
public record PacienteActualizado(UUID tenantId, UUID pacienteId, Origen origen, Instant ocurridoEn) {
}
