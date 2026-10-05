package dev.jorgemiguel.nutrikit;

import org.springframework.boot.SpringApplication;

/** Corre la app localmente con un Postgres de Testcontainers en lugar de compose.yaml. */
public class TestNutrikitApplication {

	public static void main(String[] args) {
		SpringApplication.from(NutrikitApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
