package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;

/** Clasificación del IMC en adultos según la OMS. */
public enum CategoriaImc {

	BAJO_PESO, NORMAL, SOBREPESO, OBESIDAD_I, OBESIDAD_II, OBESIDAD_III;

	public static CategoriaImc de(BigDecimal imc) {
		throw new UnsupportedOperationException("Pendiente: TDD en rojo");
	}

}
