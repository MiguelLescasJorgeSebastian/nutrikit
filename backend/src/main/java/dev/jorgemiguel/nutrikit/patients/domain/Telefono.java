package dev.jorgemiguel.nutrikit.patients.domain;

import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Teléfono normalizado: solo dígitos, con {@code +} opcional al inicio para la lada
 * internacional. Se aceptan espacios, guiones, puntos y paréntesis al capturarlo
 * ({@code (55) 1234-5678} → {@code 5512345678}), así la búsqueda por teléfono no depende de
 * cómo se escribió.
 */
@Embeddable
public record Telefono(@Column(name = "telefono", length = 20) String valor) {

	private static final Pattern SEPARADORES = Pattern.compile("[\\s().-]");
	private static final Pattern FORMATO = Pattern.compile("^\\+?\\d{10,15}$");

	public Telefono {
		if (valor == null || !FORMATO.matcher(valor).matches()) {
			throw new DatoInvalidoException("telefono", "debe tener de 10 a 15 dígitos, con + opcional al inicio");
		}
	}

	/** {@code null} si no hay teléfono. */
	public static Telefono deTexto(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		return new Telefono(SEPARADORES.matcher(texto.strip()).replaceAll(""));
	}

}
