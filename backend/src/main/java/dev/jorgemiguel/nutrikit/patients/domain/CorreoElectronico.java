package dev.jorgemiguel.nutrikit.patients.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Correo en minúsculas y sin espacios. En la base es {@code citext}. */
@Embeddable
public record CorreoElectronico(@Column(name = "email", columnDefinition = "citext") String valor) {

	/** Validación de forma, no de existencia: eso solo lo confirma un correo de verificación. */
	private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public CorreoElectronico {
		if (valor == null || valor.length() > 254 || !FORMATO.matcher(valor).matches()) {
			throw new DatoInvalidoException("email", "no es un correo válido");
		}
	}

	/** {@code null} si no hay correo. */
	public static CorreoElectronico deTexto(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		return new CorreoElectronico(texto.strip().toLowerCase(Locale.ROOT));
	}

}
