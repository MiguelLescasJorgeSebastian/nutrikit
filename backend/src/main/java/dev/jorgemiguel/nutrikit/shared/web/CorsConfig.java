package dev.jorgemiguel.nutrikit.shared.web;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS para {@code /api/**}. En producción el frontend se sirve desde el mismo subdominio que la
 * API y no hace falta: la lista viene vacía y ningún otro origen puede llamar a la API desde un
 * navegador. En desarrollo se permite solo el servidor de Vite.
 *
 * <p>Es un filtro propio y no {@code .cors()} de Spring Security porque debe ir antes que todo: antes
 * de resolver el consultorio (un preflight no lleva {@code X-Tenant}) y antes de las dos cadenas de
 * seguridad. Así también los errores de esos filtros (consultorio inexistente, 401) llevan las
 * cabeceras CORS y el frontend puede leerlos.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsConfig.CorsProperties.class)
public class CorsConfig {

	/** @param origenesPermitidos orígenes exactos, sin comodines: con credenciales, {@code *} no aplica */
	@ConfigurationProperties("nutrikit.cors")
	public record CorsProperties(@DefaultValue List<String> origenesPermitidos) {
	}

	@Bean
	FilterRegistrationBean<CorsFilter> filtroCors(CorsProperties propiedades) {
		var registro = new FilterRegistrationBean<>(new CorsFilter(origenes(propiedades)));
		registro.addUrlPatterns("/api/*");
		// Antes de la resolución del consultorio (DEFAULT_FILTER_ORDER - 10) y de Spring Security.
		registro.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 20);
		return registro;
	}

	private static CorsConfigurationSource origenes(CorsProperties propiedades) {
		var fuente = new UrlBasedCorsConfigurationSource();
		if (propiedades.origenesPermitidos().isEmpty()) {
			return fuente;
		}
		var cors = new CorsConfiguration();
		cors.setAllowedOrigins(propiedades.origenesPermitidos());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Tenant", "X-Pin"));
		cors.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER));
		// La cookie del refresh token solo viaja con credentials: 'include'.
		cors.setAllowCredentials(true);
		cors.setMaxAge(Duration.ofHours(1));
		fuente.registerCorsConfiguration("/api/**", cors);
		return fuente;
	}

}
