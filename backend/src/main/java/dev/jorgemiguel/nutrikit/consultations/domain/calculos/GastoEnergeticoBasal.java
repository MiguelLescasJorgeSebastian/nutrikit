package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** Gasto energético basal en kcal/día, con la fórmula que elija el consultorio (patrón Strategy). */
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
		var peso = entradas.medicion(Entradas.PESO);
		var talla = entradas.medicion(Entradas.TALLA);
		var edad = entradas.edadConocida();
		var sexo = entradas.sexoConocido();
		if (peso.isEmpty() || talla.isEmpty() || edad.isEmpty() || sexo.isEmpty()) {
			return Optional.empty();
		}
		// Kcal enteras: un decimal no tiene significado clínico en una estimación así.
		return Optional.of(formula.kcalPorDia(peso.get(), talla.get(), edad.get(), sexo.get()).setScale(0, RoundingMode.HALF_UP));
	}

}
