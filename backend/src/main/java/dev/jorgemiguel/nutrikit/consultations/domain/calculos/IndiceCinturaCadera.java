package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.util.Optional;

public final class IndiceCinturaCadera implements Calculo {

	public static final String CODIGO = "indice_cintura_cadera";

	@Override
	public String indicador() {
		return CODIGO;
	}

	@Override
	public Optional<BigDecimal> calcular(Entradas entradas) {
		throw new UnsupportedOperationException("Pendiente: TDD en rojo");
	}

	public static boolean riesgoAumentado(BigDecimal indice, Sexo sexo) {
		throw new UnsupportedOperationException("Pendiente: TDD en rojo");
	}

}
