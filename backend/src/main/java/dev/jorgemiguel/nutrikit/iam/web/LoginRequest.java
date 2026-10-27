package dev.jorgemiguel.nutrikit.iam.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciales")
record LoginRequest(

		@Schema(example = "ana@nutriana.mx")
		@NotBlank(message = "es obligatorio")
		@Email(message = "no es un correo válido")
		String email,

		@Schema(example = "una-contraseña-larga", format = "password")
		@NotBlank(message = "es obligatoria")
		@Size(max = 72, message = "admite máximo {max} caracteres")
		String password) {

	/** Un record imprime todos sus campos; este nunca imprime la contraseña. */
	@Override
	public String toString() {
		return "LoginRequest[email=%s, password=****]".formatted(email);
	}

}
