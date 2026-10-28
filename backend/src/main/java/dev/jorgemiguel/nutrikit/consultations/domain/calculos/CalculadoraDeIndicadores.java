package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aplica todos los cálculos y devuelve los que tuvieron datos suficientes, por código de indicador. */
public final class CalculadoraDeIndicadores {

	private final List<Calculo> calculos;

	public CalculadoraDeIndicadores(List<Calculo> calculos) {
		this.calculos = List.copyOf(calculos);
	}

	public static CalculadoraDeIndicadores con(FormulaGeb formulaGeb) {
		return new CalculadoraDeIndicadores(List.of(new IndiceDeMasaCorporal(), new IndiceCinturaCadera(),
				new GastoEnergeticoBasal(formulaGeb.formula())));
	}

	public Map<String, BigDecimal> calcular(Entradas entradas) {
		Map<String, BigDecimal> resultado = new LinkedHashMap<>();
		for (Calculo calculo : calculos) {
			calculo.calcular(entradas).ifPresent(valor -> resultado.put(calculo.indicador(), valor));
		}
		return resultado;
	}

}
