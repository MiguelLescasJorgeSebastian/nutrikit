package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CalculadoraDeIndicadores {

	private final List<Calculo> calculos;

	public CalculadoraDeIndicadores(List<Calculo> calculos) {
		this.calculos = List.copyOf(calculos);
	}

	public static CalculadoraDeIndicadores con(FormulaGeb formulaGeb) {
		throw new UnsupportedOperationException("Pendiente: TDD en rojo");
	}

	public Map<String, BigDecimal> calcular(Entradas entradas) {
		throw new UnsupportedOperationException("Pendiente: TDD en rojo");
	}

}
