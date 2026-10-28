package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.util.Optional;

public final class GastoEnergeticoBasal implements Calculo {

	public static final String CODIGO = "gasto_energetico_basal";

	private final FormulaGastoEnergetico formula;

	public GastoEnergeticoBasal(FormulaGastoEnergetico formula) {
		this.formula = formula;
	}

	@Override
	public String indicador() {
		return CODIGO;
	}

	@Override
	public Optional<BigDecimal> calcular(Entradas entradas) {
		throw new UnsupportedOperationException("Pendiente: TDD en rojo");
	}

}
