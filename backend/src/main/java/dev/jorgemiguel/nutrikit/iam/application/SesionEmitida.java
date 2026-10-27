package dev.jorgemiguel.nutrikit.iam.application;

import java.time.Instant;

/**
 * Tokens entregados al iniciar sesión o al renovar.
 *
 * @param refreshToken {@code null} para el operador, que no tiene refresh token
 */
public record SesionEmitida(String accessToken, Instant accessExpiraEn, String refreshToken, Instant refreshExpiraEn) {

	/** Sin los tokens, para que nunca terminen en un log. */
	@Override
	public String toString() {
		return "SesionEmitida[accessExpiraEn=%s, refreshExpiraEn=%s]".formatted(accessExpiraEn, refreshExpiraEn);
	}

}
