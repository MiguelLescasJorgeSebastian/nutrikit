package dev.jorgemiguel.nutrikit.patients.domain;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

/**
 * Búsqueda por nombre, apellidos o teléfono, sin importar acentos ni mayúsculas.
 *
 * <p>Cada palabra de la búsqueda debe aparecer en {@code texto_busqueda}: "jose per" encuentra a
 * "José Pérez". El término se normaliza en la base con la misma función que la columna
 * ({@code inmutable_unaccent} y {@code lower}), así Java y Postgres nunca difieren en qué es un
 * acento. El índice trigram acelera {@code LIKE '%termino%'} desde 3 letras.
 */
public final class BusquedaDePacientes {

	/** Más palabras no mejoran la búsqueda y cada una agrega una condición. */
	private static final int MAXIMO_DE_TERMINOS = 5;

	private BusquedaDePacientes() {
	}

	public static Specification<Paciente> por(String texto) {
		List<String> terminos = terminos(texto);
		if (terminos.isEmpty()) {
			return (raiz, consulta, cb) -> cb.conjunction();
		}
		return (raiz, consulta, cb) -> {
			Expression<String> columna = raiz.get("textoBusqueda");
			Predicate[] condiciones = terminos.stream().map(termino -> {
				Expression<String> normalizado = cb.function("inmutable_unaccent", String.class, cb.lower(cb.literal(escapar(termino))));
				Expression<String> patron = cb.concat(cb.concat("%", normalizado), "%");
				return cb.like(columna, patron, '\\');
			}).toArray(Predicate[]::new);
			return cb.and(condiciones);
		};
	}

	static List<String> terminos(String texto) {
		if (texto == null || texto.isBlank()) {
			return List.of();
		}
		return Arrays.stream(texto.strip().split("\\s+")).limit(MAXIMO_DE_TERMINOS).toList();
	}

	/** {@code %} y {@code _} son comodines de LIKE; se buscan como texto literal. */
	static String escapar(String termino) {
		return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
