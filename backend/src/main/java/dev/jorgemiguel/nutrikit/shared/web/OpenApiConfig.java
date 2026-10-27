package dev.jorgemiguel.nutrikit.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Datos generales del contrato OpenAPI que muestra Swagger UI. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	@Bean
	OpenAPI nutrikitOpenApi() {
		return new OpenAPI().info(new Info()
				.title("nutrikit API")
				.description("API de la plataforma multi-tenant para consultorios de nutrición. "
						+ "Los errores siguen el formato Problem Details (RFC 9457).")
				.version("0.1.0"))
				// Botón "Authorize" de Swagger UI: pega ahí el accessToken del login.
				.components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")));
	}

}
