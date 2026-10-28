package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/** Circunferencia de cintura / circunferencia de cadera. */
public final class IndiceCinturaCadera implements Calculo {

	public static final String CODIGO = "indice_cintura_cadera";
	private static final BigDecimal RIESGO_HOMBRES = new BigDecimal("0.90");
	private static final BigDecimal RIESGO_MUJERES = new BigDecimal("0.85");

	@Override
	public String indicador() {
		return CODIGO;
	}

	@Override
	public Optional<BigDecimal> calcular(Entradas entradas) {
		var cintura = entradas.medicion("circunferencia_cintura");
		var cadera = entradas.medicion("circunferencia_cadera");
		if (cintura.isEmpty() || cadera.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(cintura.get().divide(cadera.get(), MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP));
	}

	/** Riesgo metabólico sustancialmente aumentado según la OMS (2008). */
	public static boolean riesgoAumentado(BigDecimal indice, Sexo sexo) {
		BigDecimal corte = sexo == Sexo.MASCULINO ? RIESGO_HOMBRES : RIESGO_MUJERES;
		return indice.compareTo(corte) >= 0;
	}

}
