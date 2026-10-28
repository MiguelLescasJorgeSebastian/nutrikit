package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/** IMC = peso (kg) / talla (m)². La talla se captura en centímetros. */
public final class IndiceDeMasaCorporal implements Calculo {

	public static final String CODIGO = "imc";
	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	@Override
	public String indicador() {
		return CODIGO;
	}

	@Override
	public Optional<BigDecimal> calcular(Entradas entradas) {
		var peso = entradas.medicion("peso");
		var talla = entradas.medicion("talla");
		if (peso.isEmpty() || talla.isEmpty()) {
			return Optional.empty();
		}
		BigDecimal metros = talla.get().divide(CIEN, MathContext.DECIMAL64);
		return Optional.of(peso.get().divide(metros.multiply(metros), MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP));
	}

}
