package dev.jorgemiguel.nutrikit.patients.domain;

import java.time.LocalDate;

/**
 * Datos generales del paciente (RF-PAC-02), ya validados y normalizados. Se construye con
 * {@link #de} desde lo que captura el usuario.
 *
 * <p>Teléfono, correo y ocupación son opcionales: un consultorio puede registrar a alguien que
 * llega sin ellos. Nombre, apellidos, fecha de nacimiento y sexo son obligatorios porque los
 * cálculos clínicos dependen de los dos últimos.
 */
public record DatosPersonales(String nombre, String apellidos, LocalDate fechaNacimiento, Sexo sexo,
		Telefono telefono, CorreoElectronico email, String ocupacion) {

	static final LocalDate FECHA_MINIMA = LocalDate.of(1900, 1, 1);

	public static DatosPersonales de(String nombre, String apellidos, LocalDate fechaNacimiento, Sexo sexo,
			String telefono, String email, String ocupacion, LocalDate hoy) {
		if (fechaNacimiento == null) {
			throw new DatoInvalidoException("fechaNacimiento", "es obligatoria");
		}
		if (fechaNacimiento.isAfter(hoy)) {
			throw new DatoInvalidoException("fechaNacimiento", "no puede ser futura");
		}
		if (fechaNacimiento.isBefore(FECHA_MINIMA)) {
			throw new DatoInvalidoException("fechaNacimiento", "no puede ser anterior a 1900");
		}
		if (sexo == null) {
			throw new DatoInvalidoException("sexo", "es obligatorio");
		}
		return new DatosPersonales(
				texto("nombre", nombre, 100, true),
				texto("apellidos", apellidos, 150, true),
				fechaNacimiento,
				sexo,
				Telefono.deTexto(telefono),
				CorreoElectronico.deTexto(email),
				texto("ocupacion", ocupacion, 100, false));
	}

	/** Sin espacios al inicio, al final ni repetidos: "  José   Luis " → "José Luis". */
	private static String texto(String campo, String valor, int maximo, boolean obligatorio) {
		if (valor == null || valor.isBlank()) {
			if (obligatorio) {
				throw new DatoInvalidoException(campo, "es obligatorio");
			}
			return null;
		}
		String limpio = valor.strip().replaceAll("\\s+", " ");
		if (limpio.length() > maximo) {
			throw new DatoInvalidoException(campo, "admite máximo %d caracteres".formatted(maximo));
		}
		return limpio;
	}

}
