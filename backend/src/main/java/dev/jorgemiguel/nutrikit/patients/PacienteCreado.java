package dev.jorgemiguel.nutrikit.patients;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se dio de alta un paciente en un consultorio. Solo lleva identificadores: los datos personales no viajan en eventos. */
public record PacienteCreado(UUID tenantId, UUID pacienteId, Origen origen, Instant ocurridoEn) {
}
