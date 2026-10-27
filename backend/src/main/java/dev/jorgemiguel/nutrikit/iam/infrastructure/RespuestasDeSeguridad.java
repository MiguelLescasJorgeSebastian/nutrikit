package dev.jorgemiguel.nutrikit.iam.infrastructure;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import tools.jackson.databind.json.JsonMapper;

/**
 * 401 y 403 en formato Problem Details, igual que el resto de la API. Spring Security los
 * genera antes de llegar a Spring MVC, así que el {@code @RestControllerAdvice} no los ve.
 *
 * <p>Delegan primero en las implementaciones de OAuth2 para conservar la cabecera
 * {@code WWW-Authenticate} que indica al cliente por qué falló el token.
 */
class RespuestasDeSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
	private final BearerTokenAccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
	private final JsonMapper json;

	RespuestasDeSeguridad(JsonMapper json) {
		this.json = json;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		bearerEntryPoint.commence(request, response, ex);
		escribir(request, response, HttpStatus.UNAUTHORIZED, "No autenticado",
				"Se requiere un access token válido en la cabecera Authorization: Bearer <token>");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
			throws IOException {
		bearerAccessDenied.handle(request, response, ex);
		escribir(request, response, HttpStatus.FORBIDDEN, "Sin permiso",
				"Tu usuario no tiene permiso para este recurso o no pertenece a este consultorio");
	}

	private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus estado, String titulo,
			String detalle) throws IOException {
		Map<String, Object> problema = new LinkedHashMap<>();
		problema.put("title", titulo);
		problema.put("status", estado.value());
		problema.put("detail", detalle);
		problema.put("instance", request.getRequestURI());
		response.setStatus(estado.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		json.writeValue(response.getOutputStream(), problema);
	}

}
