package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Lo disponible para calcular: las mediciones de la consulta por código de indicador y, si se
 * conocen, la edad y el sexo del paciente. Cualquier dato puede faltar.
 */
public record Entradas(Map<String, BigDecimal> mediciones, Integer edad, Sexo sexo) {

	public Entradas {
		mediciones = Map.copyOf(mediciones);
	}

	public static Entradas de(Map<String, BigDecimal> mediciones) {
		return new Entradas(mediciones, null, null);
	}

	public Optional<BigDecimal> medicion(String codigo) {
		return Optional.ofNullable(mediciones.get(codigo));
	}

	public Optional<Integer> edadConocida() {
		return Optional.ofNullable(edad);
	}

	public Optional<Sexo> sexoConocido() {
		return Optional.ofNullable(sexo);
	}

}
