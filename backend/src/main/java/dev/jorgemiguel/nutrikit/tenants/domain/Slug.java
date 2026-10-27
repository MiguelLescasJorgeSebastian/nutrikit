package dev.jorgemiguel.nutrikit.tenants.domain;

import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Identificador público del consultorio; se usa como subdominio ({@code slug.nutrikit.mx}).
 *
 * <p>Solo minúsculas, números y guiones, de 3 a 60 caracteres. Sin guion al inicio, al final
 * ni dos seguidos, porque no serían una etiqueta DNS válida o legible.
 */
@Embeddable
public record Slug(@Column(name = "slug") String valor) {

	public static final int LONGITUD_MINIMA = 3;
	public static final int LONGITUD_MAXIMA = 60;

	private static final Pattern FORMATO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

	public Slug {
		if (valor == null) {
			throw new SlugInvalidoException(null, "es obligatorio");
		}
		if (valor.length() < LONGITUD_MINIMA || valor.length() > LONGITUD_MAXIMA) {
			throw new SlugInvalidoException(valor,
					"debe tener entre %d y %d caracteres".formatted(LONGITUD_MINIMA, LONGITUD_MAXIMA));
		}
		if (!FORMATO.matcher(valor).matches()) {
			throw new SlugInvalidoException(valor,
					"solo admite minúsculas, números y guiones, sin guion al inicio, al final ni repetido");
		}
	}

	public static Slug of(String valor) {
		return new Slug(valor);
	}

	@Override
	public String toString() {
		return valor;
	}

}
