package dev.jorgemiguel.nutrikit.patients.application;

import java.util.UUID;

/** No existe un paciente activo con ese id en el consultorio (o es de otro consultorio). */
public class PacienteNoEncontradoException extends RuntimeException {

	public PacienteNoEncontradoException(UUID id) {
		super("No existe un paciente con id %s".formatted(id));
	}

}
