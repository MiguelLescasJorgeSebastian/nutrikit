package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;

public final class MifflinStJeor implements FormulaGastoEnergetico {

	@Override
	public BigDecimal kcalPorDia(BigDecimal pesoKg, BigDecimal tallaCm, int edad, Sexo sexo) {
		throw new UnsupportedOperationException("Pendiente: TDD en rojo");
	}

}
