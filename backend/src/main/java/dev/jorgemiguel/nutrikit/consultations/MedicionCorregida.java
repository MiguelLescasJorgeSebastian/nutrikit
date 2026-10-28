package dev.jorgemiguel.nutrikit.consultations;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Se corrigió el valor de una medición capturada (y se recalcularon sus derivados). */
public record MedicionCorregida(UUID tenantId, UUID consultaId, String indicador, Origen origen, Instant ocurridoEn) {
}
