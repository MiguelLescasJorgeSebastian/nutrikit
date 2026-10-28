package dev.jorgemiguel.nutrikit.patients;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Un paciente se dio de baja (lógica): deja de aparecer, pero su expediente se conserva. Solo lleva identificadores: los datos personales no viajan en eventos. */
public record PacienteDadoDeBaja(UUID tenantId, UUID pacienteId, Origen origen, Instant ocurridoEn) {
}
