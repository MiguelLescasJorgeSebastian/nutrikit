package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.util.Optional;

public final class IndiceDeMasaCorporal implements Calculo {

	public static final String CODIGO = "imc";

	@Override
	public String indicador() {
		return CODIGO;
	}

	@Override
	public Optional<BigDecimal> calcular(Entradas entradas) {
		throw new UnsupportedOperationException("Pendiente: TDD en rojo");
	}

}
