package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.util.Optional;

/** Un indicador derivado de otros datos. Vacío si falta algún dato de entrada; nunca lanza por eso. */
public interface Calculo {

	/** Código del indicador calculado en el catálogo. */
	String indicador();

	Optional<BigDecimal> calcular(Entradas entradas);

}
