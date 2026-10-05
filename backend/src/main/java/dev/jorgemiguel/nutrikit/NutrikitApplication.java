package dev.jorgemiguel.nutrikit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de nutrikit.
 *
 * <p>Monolito modular: cada subpaquete directo de {@code dev.jorgemiguel.nutrikit}
 * es un módulo de aplicación verificado por Spring Modulith (ver {@code ModularityTests}).
 */
@SpringBootApplication
public class NutrikitApplication {

	public static void main(String[] args) {
		SpringApplication.run(NutrikitApplication.class, args);
	}

}
