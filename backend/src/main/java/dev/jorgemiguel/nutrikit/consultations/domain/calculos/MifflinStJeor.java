package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;

/**
 * Mifflin MD et al., Am J Clin Nutr 1990;51:241-7. Más precisa que Harris-Benedict en población
 * actual, incluidas personas con sobrepeso; es la que recomienda la Academy of Nutrition and Dietetics.
 */
public final class MifflinStJeor implements FormulaGastoEnergetico {

	private static final BigDecimal POR_KG = new BigDecimal("10");
	private static final BigDecimal POR_CM = new BigDecimal("6.25");
	private static final BigDecimal POR_ANIO = new BigDecimal("5");
	private static final BigDecimal AJUSTE_HOMBRES = new BigDecimal("5");
	private static final BigDecimal AJUSTE_MUJERES = new BigDecimal("-161");

	@Override
	public BigDecimal kcalPorDia(BigDecimal pesoKg, BigDecimal tallaCm, int edad, Sexo sexo) {
		return POR_KG.multiply(pesoKg)
				.add(POR_CM.multiply(tallaCm))
				.subtract(POR_ANIO.multiply(BigDecimal.valueOf(edad)))
				.add(sexo == Sexo.MASCULINO ? AJUSTE_HOMBRES : AJUSTE_MUJERES);
	}

}
