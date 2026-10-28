package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;

/** Estrategia para estimar el gasto energético basal (kcal/día). */
public interface FormulaGastoEnergetico {

	/**
	 * @param pesoKg peso en kilogramos
	 * @param tallaCm talla en centímetros
	 * @param edad años cumplidos
	 */
	BigDecimal kcalPorDia(BigDecimal pesoKg, BigDecimal tallaCm, int edad, Sexo sexo);

}
