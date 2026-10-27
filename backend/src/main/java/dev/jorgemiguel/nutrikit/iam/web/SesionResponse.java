package dev.jorgemiguel.nutrikit.iam.web;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.jorgemiguel.nutrikit.iam.application.SesionEmitida;

@Schema(description = "Tokens de la sesión")
@JsonInclude(JsonInclude.Include.NON_NULL)
record SesionResponse(

		@Schema(description = "JWT para la cabecera Authorization: Bearer <token>")
		String accessToken,

		@Schema(example = "Bearer")
		String tipo,

		@Schema(description = "Vencimiento del access token (15 minutos)", example = "2026-10-27T18:45:00Z")
		Instant expiraEn,

		@Schema(description = "Se usa una sola vez en /api/auth/refresh. No viene en la sesión del operador.")
		String refreshToken,

		@Schema(example = "2026-11-10T18:30:00Z")
		Instant refreshExpiraEn) {

	static SesionResponse de(SesionEmitida sesion) {
		return new SesionResponse(sesion.accessToken(), "Bearer", sesion.accessExpiraEn(), sesion.refreshToken(),
				sesion.refreshExpiraEn());
	}

	@Override
	public String toString() {
		return "SesionResponse[expiraEn=%s]".formatted(expiraEn);
	}

}
