package dev.jorgemiguel.nutrikit.consultations;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Un consultorio agregó un indicador propio a su catálogo. */
public record IndicadorCreado(UUID tenantId, UUID indicadorId, String codigo, Origen origen, Instant ocurridoEn) {
}
