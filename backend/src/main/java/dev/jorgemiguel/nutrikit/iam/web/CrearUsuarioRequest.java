package dev.jorgemiguel.nutrikit.iam.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dev.jorgemiguel.nutrikit.iam.domain.RolUsuario;

@Schema(description = "Usuario nuevo de un consultorio")
record CrearUsuarioRequest(

		@Schema(example = "ana@nutriana.mx")
		@NotBlank(message = "es obligatorio")
		@Email(message = "no es un correo válido")
		@Size(max = 254, message = "admite máximo {max} caracteres")
		String email,

		@Schema(example = "Ana López")
		@NotBlank(message = "es obligatorio")
		@Size(max = 150, message = "admite máximo {max} caracteres")
		String nombre,

		@Schema(example = "TITULAR")
		@NotNull(message = "es obligatorio")
		RolUsuario rol,

		/** Mínimo 12 (NIST recomienda longitud sobre complejidad); máximo 72, el límite de BCrypt. */
		@Schema(description = "Contraseña inicial, de 12 a 72 caracteres", format = "password")
		@NotBlank(message = "es obligatoria")
		@Size(min = 12, max = 72, message = "debe tener entre {min} y {max} caracteres")
		String password) {

	@Override
	public String toString() {
		return "CrearUsuarioRequest[email=%s, rol=%s, password=****]".formatted(email, rol);
	}

}
