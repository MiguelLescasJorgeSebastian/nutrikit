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

	/** Genera diagramas C4 y fichas de cada módulo en target/spring-modulith-docs. */
	@Test
	void writesDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
