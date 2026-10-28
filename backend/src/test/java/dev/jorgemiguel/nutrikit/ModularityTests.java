package dev.jorgemiguel.nutrikit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyDepth;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica los límites entre módulos: ningún módulo puede usar clases internas de otro
 * ni formar dependencias cíclicas. Si alguien rompe una regla, el build falla.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(NutrikitApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

	/**
	 * La bitácora solo escucha: depende de tenants e iam como listener de sus eventos, y ningún
	 * módulo depende de ella. Se podría apagar o extraer sin tocar a los demás.
	 */
	@Test
	void auditoriaSoloSeConectaPorEventos() {
		var auditoria = modules.getModuleByName("audit").orElseThrow();
		var comoListener = auditoria.getDependencies(modules, DependencyDepth.IMMEDIATE, DependencyType.EVENT_LISTENER);

		assertThat(comoListener.containsModuleNamed("tenants")).isTrue();
		assertThat(comoListener.containsModuleNamed("iam")).isTrue();
		assertThat(modules.stream().filter(modulo -> !modulo.equals(auditoria))
				.filter(modulo -> modulo.getDependencies(modules, DependencyDepth.IMMEDIATE).contains(auditoria)))
				.isEmpty();
	}

	/**
	 * Consultas guarda el paciente_id y pregunta por él a la API pública de pacientes; nunca usa sus
	 * clases internas (verify() fallaría) y pacientes no depende de consultas.
	 */
	@Test
	void consultasUsaPacientesSoloPorSuApiPublica() {
		var consultas = modules.getModuleByName("consultations").orElseThrow();
		var pacientes = modules.getModuleByName("patients").orElseThrow();

		assertThat(consultas.getDependencies(modules, DependencyDepth.IMMEDIATE).contains(pacientes)).isTrue();
		assertThat(pacientes.getDependencies(modules, DependencyDepth.IMMEDIATE).contains(consultas)).isFalse();
		assertThat(consultas.getDependencies(modules, DependencyDepth.IMMEDIATE).stream()
				.filter(dependencia -> dependencia.getTargetModule().equals(pacientes))
				.map(dependencia -> dependencia.getTargetType().getPackageName()))
				.allMatch("dev.jorgemiguel.nutrikit.patients"::equals);
	}

	/** Genera diagramas C4 y fichas de cada módulo en target/spring-modulith-docs. */
	@Test
	void writesDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
