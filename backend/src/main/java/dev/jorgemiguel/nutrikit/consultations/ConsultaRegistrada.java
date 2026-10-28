package dev.jorgemiguel.nutrikit.consultations;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se registró una consulta con sus mediciones. Solo identificadores. */
public record ConsultaRegistrada(UUID tenantId, UUID consultaId, UUID pacienteId, Origen origen, Instant ocurridoEn) {
}
