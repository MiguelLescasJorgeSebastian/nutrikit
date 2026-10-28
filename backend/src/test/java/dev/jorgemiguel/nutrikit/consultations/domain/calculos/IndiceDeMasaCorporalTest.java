package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * IMC = peso (kg) / talla (m)².
 *
 * <p>Fuente: Organización Mundial de la Salud, "Obesidad y sobrepeso", nota descriptiva (2024),
 * https://www.who.int/es/news-room/fact-sheets/detail/obesity-and-overweight, y WHO Technical
 * Report Series 894, "Obesity: preventing and managing the global epidemic" (2000), tabla 2.1
 * (clasificación en adultos). Los valores esperados se calcularon con la fórmula de la OMS y se
 * redondean a 2 decimales (mitad hacia arriba).
 */
class IndiceDeMasaCorporalTest {

	private final IndiceDeMasaCorporal imc = new IndiceDeMasaCorporal();

	private static Entradas pesoYTalla(String peso, String talla) {
		return Entradas.de(Map.of("peso", new BigDecimal(peso), "talla", new BigDecimal(talla)));
	}

	@ParameterizedTest(name = "{0} kg y {1} cm → IMC {2}")
	@CsvSource({
			"70,   175, 22.86",
			"50,   165, 18.37",
			"95,   180, 29.32",
			"120,  170, 41.52",
			"82.5, 175, 26.94",
			"60,   160, 23.44" })
	void calculaElImcConTallaEnCentimetros(String peso, String talla, BigDecimal esperado) {
		assertThat(imc.calcular(pesoYTalla(peso, talla))).contains(esperado);
	}

	@Test
	void sinTallaOSinPesoNoSeCalculaYNoHayError() {
		assertThat(imc.calcular(Entradas.de(Map.of("peso", new BigDecimal("70"))))).isEmpty();
		assertThat(imc.calcular(Entradas.de(Map.of("talla", new BigDecimal("175"))))).isEmpty();
		assertThat(imc.calcular(Entradas.de(Map.of()))).isEmpty();
	}

	/** Puntos de corte de la OMS (TRS 894, tabla 2.1). */
	@ParameterizedTest(name = "IMC {0} → {1}")
	@CsvSource({
			"18.49, BAJO_PESO",
			"18.5,  NORMAL",
			"24.99, NORMAL",
			"25,    SOBREPESO",
			"29.99, SOBREPESO",
			"30,    OBESIDAD_I",
			"35,    OBESIDAD_II",
			"40,    OBESIDAD_III",
			"52.3,  OBESIDAD_III" })
	void clasificaSegunLaOms(BigDecimal valor, CategoriaImc categoria) {
		assertThat(CategoriaImc.de(valor)).isEqualTo(categoria);
	}

	@Test
	void elCodigoEsElDelCatalogo() {
		assertThat(imc.indicador()).isEqualTo("imc");
	}

}
