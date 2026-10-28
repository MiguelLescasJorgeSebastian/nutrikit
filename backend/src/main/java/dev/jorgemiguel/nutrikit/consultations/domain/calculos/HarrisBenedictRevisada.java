package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;

/** Harris-Benedict con los coeficientes revisados de Roza y Shizgal, Am J Clin Nutr 1984;40:168-82. */
public final class HarrisBenedictRevisada implements FormulaGastoEnergetico {

	private record Coeficientes(BigDecimal base, BigDecimal porKg, BigDecimal porCm, BigDecimal porAnio) {
	}

	private static final Coeficientes HOMBRES = new Coeficientes(new BigDecimal("88.362"), new BigDecimal("13.397"),
			new BigDecimal("4.799"), new BigDecimal("5.677"));
	private static final Coeficientes MUJERES = new Coeficientes(new BigDecimal("447.593"), new BigDecimal("9.247"),
			new BigDecimal("3.098"), new BigDecimal("4.330"));

	@Override
	public BigDecimal kcalPorDia(BigDecimal pesoKg, BigDecimal tallaCm, int edad, Sexo sexo) {
		Coeficientes c = sexo == Sexo.MASCULINO ? HOMBRES : MUJERES;
		return c.base()
				.add(c.porKg().multiply(pesoKg))
				.add(c.porCm().multiply(tallaCm))
				.subtract(c.porAnio().multiply(BigDecimal.valueOf(edad)));
	}

}
