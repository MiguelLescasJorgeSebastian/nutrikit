package dev.jorgemiguel.nutrikit.patientaccess.application;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.jorgemiguel.nutrikit.shared.LimitadorDeIntentos;

/**
 * Argon2id para el PIN (ADR-0003): un PIN tiene pocas combinaciones, así que su hash debe ser
 * caro de calcular, en tiempo y en memoria. Es otro bean distinto del {@code PasswordEncoder} del
 * módulo iam para que cambiar uno no afecte al otro.
 */
public record PinEncoder(PasswordEncoder encoder) {

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		PinEncoder pinEncoder() {
			return new PinEncoder(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
		}

		/** Límites propios del enlace, separados de los del inicio de sesión. */
		@Bean
		LimitadorDeIntentos limitadorDeEnlaces(Clock clock) {
			return new LimitadorDeIntentos(Duration.ofMinutes(15), clock);
		}

	}

}
