package dev.jorgemiguel.nutrikit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Levanta el contexto completo contra un Postgres real (Testcontainers) y corre las migraciones. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class NutrikitApplicationTests {

	@Test
	void contextLoads() {
	}

}
