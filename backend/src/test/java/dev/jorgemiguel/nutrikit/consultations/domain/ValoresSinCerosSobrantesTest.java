package dev.jorgemiguel.nutrikit.consultations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Regresión: stripTrailingZeros convierte 80 en 8E+1 y así salía en el JSON. */
class ValoresSinCerosSobrantesTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "80, 80", "80.000, 80", "72.40, 72.4", "100, 100", "0.500, 0.5", "1500, 1500" })
	void nuncaUsaNotacionCientifica(BigDecimal valor, String esperado) {
		assertThat(Indicador.sinCerosSobrantes(valor).toString()).isEqualTo(esperado);
	}

}
