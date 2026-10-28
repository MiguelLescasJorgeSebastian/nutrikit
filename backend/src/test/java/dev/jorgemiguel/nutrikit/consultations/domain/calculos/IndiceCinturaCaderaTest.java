package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Índice cintura-cadera = circunferencia de cintura / circunferencia de cadera (mismas unidades).
 *
 * <p>Fuente: World Health Organization, "Waist circumference and waist–hip ratio: report of a WHO
 * expert consultation", Ginebra, 8–11 de diciembre de 2008 (publicado en 2011), tabla 1: riesgo
 * metabólico sustancialmente aumentado con índice ≥ 0.90 en hombres y ≥ 0.85 en mujeres. Valores
 * redondeados a 2 decimales (mitad hacia arriba).
 */
class IndiceCinturaCaderaTest {

	private final IndiceCinturaCadera icc = new IndiceCinturaCadera();

	private static Entradas cinturaYCadera(String cintura, String cadera) {
		return Entradas.de(Map.of("circunferencia_cintura", new BigDecimal(cintura),
				"circunferencia_cadera", new BigDecimal(cadera)));
	}

	@ParameterizedTest(name = "{0} / {1} → {2}")
	@CsvSource({
			"94,   102, 0.92",
			"80,   100, 0.80",
			"85,   100, 0.85",
			"90,   100, 0.90",
			"110,  105, 1.05",
			"70.5, 98,  0.72" })
	void calculaElIndice(String cintura, String cadera, BigDecimal esperado) {
		assertThat(icc.calcular(cinturaYCadera(cintura, cadera))).contains(esperado);
	}

	@Test
	void sinCinturaOSinCaderaNoSeCalculaYNoHayError() {
		assertThat(icc.calcular(Entradas.de(Map.of("circunferencia_cintura", new BigDecimal("80"))))).isEmpty();
		assertThat(icc.calcular(Entradas.de(Map.of("circunferencia_cadera", new BigDecimal("100"))))).isEmpty();
	}

	/** Puntos de corte de la OMS (2008): ≥ 0.90 hombres, ≥ 0.85 mujeres. */
	@ParameterizedTest(name = "{0} en {1} → riesgo aumentado: {2}")
	@CsvSource({
			"0.89, MASCULINO, false",
			"0.90, MASCULINO, true",
			"1.05, MASCULINO, true",
			"0.84, FEMENINO,  false",
			"0.85, FEMENINO,  true",
			"0.88, FEMENINO,  true" })
	void identificaElRiesgoMetabolicoAumentado(BigDecimal indice, Sexo sexo, boolean riesgo) {
		assertThat(IndiceCinturaCadera.riesgoAumentado(indice, sexo)).isEqualTo(riesgo);
	}

	@Test
	void elCodigoEsElDelCatalogo() {
		assertThat(icc.indicador()).isEqualTo("indice_cintura_cadera");
	}

}
