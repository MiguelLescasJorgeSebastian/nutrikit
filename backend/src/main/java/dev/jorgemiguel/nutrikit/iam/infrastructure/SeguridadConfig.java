package dev.jorgemiguel.nutrikit.iam.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import dev.jorgemiguel.nutrikit.iam.application.ClaimsJwt;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reglas de acceso HTTP. Todo lo que no está permitido de forma explícita se niega.
 *
 * <table>
 * <tr><th>Ruta</th><th>Acceso</th></tr>
 * <tr><td>{@code /actuator/health}, Swagger</td><td>Público (Swagger solo se publica en dev)</td></tr>
 * <tr><td>{@code POST /api/operador/auth/login}</td><td>Público</td></tr>
 * <tr><td>{@code /api/operador/**}</td><td>Rol {@code OPERADOR}</td></tr>
 * <tr><td>{@code POST /api/auth/login|refresh|logout}</td><td>Público, dentro del consultorio del subdominio</td></tr>
 * <tr><td>{@code /api/**}</td><td>JWT del mismo consultorio que la petición</td></tr>
 * <tr><td>Lo demás</td><td>Negado</td></tr>
 * </table>
 */
@Configuration(proxyBeanMethods = false)
public class SeguridadConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) throws Exception {
		var respuestas = new RespuestasDeSeguridad(json);
		return http
				// API sin sesión ni cookies: el token viaja en la cabecera Authorization, así que no
				// aplica CSRF.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rutas -> rutas
						.requestMatchers("/actuator/health/**", "/error").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/operador/auth/login").permitAll()
						.requestMatchers("/api/operador/**").hasRole(ClaimsJwt.ROL_OPERADOR)
						.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
								.permitAll()
						.requestMatchers("/api/**").access(new MismoConsultorio())
						.anyRequest().denyAll())
				.oauth2ResourceServer(recurso -> recurso
						.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeRoles()))
						.authenticationEntryPoint(respuestas)
						.accessDeniedHandler(respuestas))
				.exceptionHandling(errores -> errores
						.authenticationEntryPoint(respuestas)
						.accessDeniedHandler(respuestas))
				.build();
	}

	/** El claim {@code rol} se convierte en la autoridad {@code ROLE_<rol>} para usar {@code hasRole}. */
	private static JwtAuthenticationConverter convertidorDeRoles() {
		var roles = new JwtGrantedAuthoritiesConverter();
		roles.setAuthoritiesClaimName(ClaimsJwt.ROL);
		roles.setAuthorityPrefix("ROLE_");
		var convertidor = new JwtAuthenticationConverter();
		convertidor.setJwtGrantedAuthoritiesConverter(roles);
		return convertidor;
	}

}
