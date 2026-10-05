package dev.jorgemiguel.nutrikit;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
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

	/** Genera diagramas C4 y fichas de cada módulo en target/spring-modulith-docs. */
	@Test
	void writesDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
