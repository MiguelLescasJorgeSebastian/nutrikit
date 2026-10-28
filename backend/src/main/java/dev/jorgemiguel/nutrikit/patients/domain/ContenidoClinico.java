package dev.jorgemiguel.nutrikit.patients.domain;

import java.util.Map;
import java.util.TreeMap;

/**
 * Lo que dice la historia clínica. Texto libre: el formato exacto de cada sección está pendiente
 * de validar con nutriólogas (modelo de datos, "Pendiente de validar").
 *
 * @param camposExtra secciones propias del consultorio (por ejemplo, "Hábitos de sueño")
 */
public record ContenidoClinico(String antecedentes, String padecimientos, String medicamentos, String alergias,
		Map<String, String> camposExtra) {

	public static final int MAXIMO_POR_CAMPO = 10_000;
	public static final int MAXIMO_CAMPOS_EXTRA = 30;
	public static final int MAXIMO_NOMBRE_CAMPO_EXTRA = 60;

	public ContenidoClinico {
		antecedentes = texto("antecedentes", antecedentes);
		padecimientos = texto("padecimientos", padecimientos);
		medicamentos = texto("medicamentos", medicamentos);
		alergias = texto("alergias", alergias);
		camposExtra = extras(camposExtra);
	}

	private static String texto(String campo, String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.strip();
		if (limpio.length() > MAXIMO_POR_CAMPO) {
			throw new DatoInvalidoException(campo, "admite máximo %d caracteres".formatted(MAXIMO_POR_CAMPO));
		}
		return limpio;
	}

	private static Map<String, String> extras(Map<String, String> valores) {
		if (valores == null || valores.isEmpty()) {
			return Map.of();
		}
		if (valores.size() > MAXIMO_CAMPOS_EXTRA) {
			throw new DatoInvalidoException("camposExtra", "admite máximo %d campos".formatted(MAXIMO_CAMPOS_EXTRA));
		}
		Map<String, String> limpios = new TreeMap<>();
		valores.forEach((nombre, valor) -> {
			if (nombre == null || nombre.isBlank() || nombre.strip().length() > MAXIMO_NOMBRE_CAMPO_EXTRA) {
				throw new DatoInvalidoException("camposExtra",
						"cada nombre es obligatorio y admite máximo %d caracteres".formatted(MAXIMO_NOMBRE_CAMPO_EXTRA));
			}
			String limpio = texto("camposExtra", valor);
			if (limpio != null) {
				limpios.put(nombre.strip(), limpio);
			}
		});
		return Map.copyOf(limpios);
	}

	/** Sin el contenido. */
	@Override
	public String toString() {
		return "ContenidoClinico[****]";
	}

}
