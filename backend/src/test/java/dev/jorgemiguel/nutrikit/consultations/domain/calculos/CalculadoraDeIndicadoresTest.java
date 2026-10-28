package dev.jorgemiguel.nutrikit.consultations.domain.calculos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

class CalculadoraDeIndicadoresTest {

	private static final Map<String, BigDecimal> COMPLETAS = Map.of(
			"peso", new BigDecimal("70"), "talla", new BigDecimal("175"),
			"circunferencia_cintura", new BigDecimal("94"), "circunferencia_cadera", new BigDecimal("102"));

	@Test
	void calculaTodoLoQueTieneDatos() {
		var resultado = CalculadoraDeIndicadores.con(FormulaGeb.MIFFLIN_ST_JEOR)
				.calcular(new Entradas(COMPLETAS, 30, Sexo.MASCULINO));

		assertThat(resultado).containsOnly(
				Map.entry("imc", new BigDecimal("22.86")),
				Map.entry("indice_cintura_cadera", new BigDecimal("0.92")),
				Map.entry("gasto_energetico_basal", new BigDecimal("1649")));
	}

	@Test
	void omiteLoQueNoTieneDatosSinFallar() {
		var resultado = CalculadoraDeIndicadores.con(FormulaGeb.MIFFLIN_ST_JEOR)
				.calcular(Entradas.de(Map.of("peso", new BigDecimal("70"), "circunferencia_cintura", new BigDecimal("94"))));

		assertThat(resultado).isEmpty();
	}

	@Test
	void usaLaFormulaDeGastoEnergeticoElegida() {
		var resultado = CalculadoraDeIndicadores.con(FormulaGeb.HARRIS_BENEDICT)
				.calcular(new Entradas(COMPLETAS, 30, Sexo.MASCULINO));

		assertThat(resultado).containsEntry("gasto_energetico_basal", new BigDecimal("1696"));
	}

	/** Criterio del issue: los cálculos son dominio puro, sin Spring ni JPA. */
	@Test
	void lasClasesDeCalculoNoDependenDeSpringNiDeJpa() {
		var clases = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages(CalculadoraDeIndicadores.class.getPackageName());

		ArchRuleDefinition.noClasses().should().dependOnClassesThat()
				.resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
				.check(clases);
		assertThat(clases).isNotEmpty();
	}

}
