package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;

/** Clasificación del IMC en adultos según la OMS (TRS 894, tabla 2.1). */
public enum CategoriaImc {

	BAJO_PESO, NORMAL, SOBREPESO, OBESIDAD_I, OBESIDAD_II, OBESIDAD_III;

	private static final BigDecimal NORMAL_DESDE = new BigDecimal("18.5");
	private static final BigDecimal SOBREPESO_DESDE = new BigDecimal("25");
	private static final BigDecimal OBESIDAD_I_DESDE = new BigDecimal("30");
	private static final BigDecimal OBESIDAD_II_DESDE = new BigDecimal("35");
	private static final BigDecimal OBESIDAD_III_DESDE = new BigDecimal("40");

	public static CategoriaImc de(BigDecimal imc) {
		if (imc.compareTo(NORMAL_DESDE) < 0) {
			return BAJO_PESO;
		}
		if (imc.compareTo(SOBREPESO_DESDE) < 0) {
			return NORMAL;
		}
		if (imc.compareTo(OBESIDAD_I_DESDE) < 0) {
			return SOBREPESO;
		}
		if (imc.compareTo(OBESIDAD_II_DESDE) < 0) {
			return OBESIDAD_I;
		}
		return imc.compareTo(OBESIDAD_III_DESDE) < 0 ? OBESIDAD_II : OBESIDAD_III;
	}

}
