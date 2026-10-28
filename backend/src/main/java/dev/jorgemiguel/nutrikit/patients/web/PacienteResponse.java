package dev.jorgemiguel.nutrikit.patients.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.jorgemiguel.nutrikit.patients.domain.Paciente;
import dev.jorgemiguel.nutrikit.patients.domain.Sexo;

@Schema(description = "Paciente")
record PacienteResponse(

		UUID id,

		@Schema(example = "José Luis")
		String nombre,

		@Schema(example = "Pérez García")
		String apellidos,

		@Schema(example = "1988-04-17")
		LocalDate fechaNacimiento,

		@Schema(description = "Calculada a partir de la fecha de nacimiento; no se guarda", example = "38")
		int edad,

		Sexo sexo,

		@Schema(example = "5512345678", nullable = true)
		String telefono,

		@Schema(example = "jose.perez@correo.mx", nullable = true)
		String email,

		@Schema(nullable = true)
		String ocupacion,

		Instant creadoEn,

		@Schema(description = "Envíala al editar para detectar cambios de otra persona", example = "0")
		long version) {

	static PacienteResponse de(Paciente paciente, LocalDate hoy) {
		return new PacienteResponse(
				paciente.getId(),
				paciente.getNombre(),
				paciente.getApellidos(),
				paciente.getFechaNacimiento(),
				paciente.edad(hoy),
				paciente.getSexo(),
				paciente.getTelefono() == null ? null : paciente.getTelefono().valor(),
				paciente.getEmail() == null ? null : paciente.getEmail().valor(),
				paciente.getOcupacion(),
				paciente.getCreadoEn(),
				paciente.getVersion());
	}

	@Override
	public String toString() {
		return "PacienteResponse[id=%s]".formatted(id);
	}

}
