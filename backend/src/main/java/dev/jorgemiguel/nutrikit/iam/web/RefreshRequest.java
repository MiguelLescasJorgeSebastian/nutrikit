package dev.jorgemiguel.nutrikit.iam.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Refresh token recibido al iniciar sesión o en la última renovación")
record RefreshRequest(

		@Schema(example = "q3Zt0b6m1xQ4...")
		@NotBlank(message = "es obligatorio")
		@Size(max = 100, message = "admite máximo {max} caracteres")
		String refreshToken) {

	@Override
	public String toString() {
		return "RefreshRequest[refreshToken=****]";
	}

}
