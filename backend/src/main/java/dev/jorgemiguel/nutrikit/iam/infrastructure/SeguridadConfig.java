package dev.jorgemiguel.nutrikit.iam.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Reglas de acceso HTTP. Versión provisional de la fase 0: todavía no hay autenticación.
 *
 * <p>Pendiente en #8: {@code /api/operador/**} debe exigir el rol de operador y el resto de la
 * API un JWT válido.
 */
@Configuration(proxyBeanMethods = false)
public class SeguridadConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				// API REST sin sesión ni cookies: no aplica CSRF. Se autentica con JWT en #8.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rutas -> rutas
						.requestMatchers("/actuator/health/**", "/error").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						// TODO(#8): .hasRole("OPERADOR")
						.requestMatchers("/api/operador/**").permitAll()
						// TODO(#8): .authenticated() y el consultorio del JWT igual al de la petición
						.requestMatchers("/api/consultorio/**").permitAll()
						.anyRequest().authenticated())
				.build();
	}

}
