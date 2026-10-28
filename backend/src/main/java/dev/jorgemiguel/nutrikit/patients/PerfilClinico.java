package dev.jorgemiguel.nutrikit.patients;

import java.time.LocalDate;
import java.time.Period;

/**
 * Los datos del paciente que necesitan los cálculos clínicos de otros módulos, sin exponer la
 * entidad: fecha de nacimiento y sexo biológico ({@code FEMENINO} o {@code MASCULINO}).
 */
public record PerfilClinico(LocalDate fechaNacimiento, String sexoBiologico) {

	/** Edad cumplida en una fecha; por ejemplo, el día de la consulta. */
	public int edadEn(LocalDate dia) {
		return Period.between(fechaNacimiento, dia).getYears();
	}

}
