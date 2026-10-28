package dev.jorgemiguel.nutrikit.patientaccess.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Segunda cadena de Spring Security, solo para la página del paciente ({@code /api/acceso/**}). Va
 * antes ({@code @Order(1)}) que la del personal, que exige JWT para el resto de {@code /api/**}.
 *
 * <p>Aquí no hay JWT: la credencial es el enlace y la valida el servicio. Lo que sí agrega la cadena
 * son las cabeceras de privacidad de una respuesta con datos de salud:
 * <ul>
 * <li>{@code Cache-Control: no-store}: ni el navegador ni un proxy la guardan.</li>
 * <li>{@code Referrer-Policy: no-referrer}: si la página enlaza a otro sitio, el token (que va en
 * la URL) no viaja en la cabecera {@code Referer}.</li>
 * <li>{@code X-Robots-Tag: noindex, nofollow}: un buscador que la encuentre no la indexa.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SeguridadDelEnlaceConfig {

	@Bean
	@Order(1)
	SecurityFilterChain enlaceDelPaciente(HttpSecurity http) throws Exception {
		return http
				.securityMatcher("/api/acceso/**")
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(rutas -> rutas.anyRequest().permitAll())
				.headers(cabeceras -> cabeceras
						.cacheControl(Customizer.withDefaults())
						.referrerPolicy(politica -> politica.policy(ReferrerPolicy.NO_REFERRER))
						.addHeaderWriter(new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow")))
				.build();
	}

}
