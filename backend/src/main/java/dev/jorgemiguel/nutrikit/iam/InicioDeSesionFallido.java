package dev.jorgemiguel.nutrikit.iam;

import java.time.Instant;
import java.util.UUID;

import dev.jorgemiguel.nutrikit.shared.Origen;

/**
 * Un intento de inicio de sesión falló. La respuesta al cliente no dice por qué, pero la bitácora
 * sí guarda a qué cuenta se intentó entrar, para detectar ataques contra una persona.
 *
 * @param usuarioId la cuenta atacada, o {@code null} si el correo no existe en el consultorio
 */
public record InicioDeSesionFallido(UUID tenantId, UUID usuarioId, Origen origen, Instant ocurridoEn) {
}
