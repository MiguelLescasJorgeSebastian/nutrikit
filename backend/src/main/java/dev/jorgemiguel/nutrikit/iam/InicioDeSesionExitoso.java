package dev.jorgemiguel.nutrikit.iam;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/** Un usuario inició sesión en su consultorio. */
public record InicioDeSesionExitoso(UUID tenantId, UUID usuarioId, Origen origen, Instant ocurridoEn) {
}
