package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Gasto energético basal (kcal/día), con dos fórmulas intercambiables. Valores esperados
 * calculados con los coeficientes publicados, con 3 decimales exactos y redondeados a kcal enteras.
 */
class GastoEnergeticoBasalTest {

	/**
	 * Fuente: Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO. "A new predictive
	 * equation for resting energy expenditure in healthy individuals". Am J Clin Nutr.
	 * 1990;51(2):241-247. doi:10.1093/ajcn/51.2.241.
	 * Hombres: 10·peso + 6.25·talla − 5·edad + 5. Mujeres: 10·peso + 6.25·talla − 5·edad − 161.
	 */
	@Nested
	class MifflinStJeorTest {

		private final MifflinStJeor formula = new MifflinStJeor();

		@ParameterizedTest(name = "{0}, {1} kg, {2} cm, {3} años → {4} kcal")
		@CsvSource({
				"MASCULINO, 70, 175, 30, 1648.75",
				"FEMENINO,  60, 165, 25, 1345.25",
				"MASCULINO, 90, 180, 45, 1805.00",
				"FEMENINO,  80, 160, 50, 1389.00",
				"MASCULINO, 60, 170, 20, 1567.50",
				"FEMENINO,  55, 155, 70, 1007.75" })
		void coincideConLaEcuacionPublicada(Sexo sexo, BigDecimal peso, BigDecimal talla, int edad, BigDecimal esperado) {
			assertThat(formula.kcalPorDia(peso, talla, edad, sexo)).isEqualByComparingTo(esperado);
		}

	}

	/**
	 * Fuente: Roza AM, Shizgal HM. "The Harris Benedict equation reevaluated: resting energy
	 * requirements and the body cell mass". Am J Clin Nutr. 1984;40(1):168-182.
	 * doi:10.1093/ajcn/40.1.168. Revisión de Harris JA, Benedict FG (1918).
	 * Hombres: 88.362 + 13.397·peso + 4.799·talla − 5.677·edad.
	 * Mujeres: 447.593 + 9.247·peso + 3.098·talla − 4.330·edad.
	 */
	@Nested
	class HarrisBenedictRevisadaTest {

		private final HarrisBenedictRevisada formula = new HarrisBenedictRevisada();

		@ParameterizedTest(name = "{0}, {1} kg, {2} cm, {3} años → {4} kcal")
		@CsvSource({
				"MASCULINO, 70, 175, 30, 1695.667",
				"FEMENINO,  60, 165, 25, 1405.333",
				"MASCULINO, 90, 180, 45, 1902.447",
				"FEMENINO,  80, 160, 50, 1466.533",
				"MASCULINO, 60, 170, 20, 1594.472",
				"FEMENINO,  55, 155, 70, 1133.268" })
		void coincideConLaEcuacionPublicada(Sexo sexo, BigDecimal peso, BigDecimal talla, int edad, BigDecimal esperado) {
			assertThat(formula.kcalPorDia(peso, talla, edad, sexo)).isEqualByComparingTo(esperado);
		}

	}

	@Nested
	class ComoIndicador {

		private static Entradas completas(Integer edad, Sexo sexo) {
			return new Entradas(Map.of("peso", new BigDecimal("70"), "talla", new BigDecimal("175")), edad, sexo);
		}

		@Test
		void redondeaAKcalEnterasConLaFormulaElegida() {
			assertThat(new GastoEnergeticoBasal(new MifflinStJeor()).calcular(completas(30, Sexo.MASCULINO)))
					.contains(new BigDecimal("1649"));
			assertThat(new GastoEnergeticoBasal(new HarrisBenedictRevisada()).calcular(completas(30, Sexo.MASCULINO)))
					.contains(new BigDecimal("1696"));
		}

		@Test
		void sinEdadSexoPesoOTallaNoSeCalculaYNoHayError() {
			var geb = new GastoEnergeticoBasal(new MifflinStJeor());

			assertThat(geb.calcular(completas(null, Sexo.MASCULINO))).isEmpty();
			assertThat(geb.calcular(completas(30, null))).isEmpty();
			assertThat(geb.calcular(new Entradas(Map.of("peso", BigDecimal.TEN), 30, Sexo.FEMENINO))).isEmpty();
		}

		@Test
		void lasFormulasSonIntercambiablesPorConsultorio() {
			assertThat(FormulaGeb.MIFFLIN_ST_JEOR.formula().kcalPorDia(new BigDecimal("60"), new BigDecimal("165"), 25,
					Sexo.FEMENINO).setScale(0, RoundingMode.HALF_UP)).isEqualByComparingTo("1345");
			assertThat(FormulaGeb.HARRIS_BENEDICT.formula().kcalPorDia(new BigDecimal("60"), new BigDecimal("165"), 25,
					Sexo.FEMENINO).setScale(0, RoundingMode.HALF_UP)).isEqualByComparingTo("1405");
		}

	}

}
