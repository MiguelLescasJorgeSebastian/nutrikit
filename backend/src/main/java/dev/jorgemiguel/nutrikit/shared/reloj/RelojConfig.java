package dev.jorgemiguel.nutrikit.shared.reloj;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj de la aplicación. Inyectarlo en vez de llamar a {@code Instant.now()} permite fijar el tiempo en pruebas. */
@Configuration(proxyBeanMethods = false)
class RelojConfig {

	@Bean
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.systemUTC();
	}

}
