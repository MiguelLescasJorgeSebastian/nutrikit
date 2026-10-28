package dev.jorgemiguel.nutrikit.patients.web;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import dev.jorgemiguel.nutrikit.patients.domain.Sexo;

/**
 * Datos generales del paciente. Aquí se valida lo obligatorio y la longitud para responder todos
 * los errores juntos; el formato del teléfono, del correo y de la fecha lo valida el dominio.
 */
@Schema(description = "Datos generales del paciente")
record PacienteRequest(

		@Schema(example = "José Luis")
		@NotBlank(message = "es obligatorio")
		@Size(max = 100, message = "admite máximo {max} caracteres")
		String nombre,

		@Schema(example = "Pérez García")
		@NotBlank(message = "es obligatorio")
		@Size(max = 150, message = "admite máximo {max} caracteres")
		String apellidos,

		@Schema(example = "1988-04-17")
		@NotNull(message = "es obligatoria")
		LocalDate fechaNacimiento,

		@Schema(description = "Sexo biológico; lo usan los cálculos clínicos", example = "MASCULINO")
		@NotNull(message = "es obligatorio")
		Sexo sexo,

		@Schema(description = "Opcional. Se guardan solo los dígitos", example = "(55) 1234-5678")
		String telefono,

		@Schema(description = "Opcional", example = "jose.perez@correo.mx")
		String email,

		@Schema(description = "Opcional", example = "Contador")
		@Size(max = 100, message = "admite máximo {max} caracteres")
		String ocupacion,

		@Schema(description = "Obligatoria al editar: la versión que leíste. Si alguien más editó antes, responde 409",
				example = "0")
		Long version) {

	@Override
	public String toString() {
		return "PacienteRequest[version=%s]".formatted(version);
	}

}
