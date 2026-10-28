package dev.jorgemiguel.nutrikit.iam.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.iam.application.AutenticacionService;
import dev.jorgemiguel.nutrikit.iam.application.SesionEmitida;
import dev.jorgemiguel.nutrikit.iam.application.SesionInvalidaException;

/**
 * Sesión de los usuarios de un consultorio. El consultorio sale del subdominio (o de
 * {@code X-Tenant} en desarrollo): el mismo correo puede existir en dos consultorios.
 *
 * <p>El access token va en el cuerpo y el cliente lo guarda en memoria; el refresh token va solo en
 * una cookie {@code HttpOnly} (ver {@link CookieDeRefresh}), nunca en el cuerpo.
 */
@Tag(name = "Autenticación", description = "Inicio de sesión, renovación y cierre de sesión en un consultorio")
@RestController
@RequestMapping("/api/auth")
class AuthController {

	private static final String CABECERA = "X-Tenant";
	private static final String DESCRIPCION_CABECERA =
			"Slug del consultorio. Solo en desarrollo; en producción se usa el subdominio.";

	private final AutenticacionService service;
	private final CookieDeRefresh cookie;
	private final Clock clock;

	AuthController(AutenticacionService service, CookieDeRefresh cookie, Clock clock) {
		this.service = service;
		this.cookie = cookie;
		this.clock = clock;
	}

	@Operation(summary = "Iniciar sesión",
			description = "Devuelve un access token de 15 minutos y deja el refresh token en la cookie HttpOnly "
					+ CookieDeRefresh.NOMBRE + ".")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Sesión iniciada")
	@ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos; ver Retry-After",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/login")
	ResponseEntity<SesionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return conCookie(service.iniciarSesion(request.email(), request.password(), http.getRemoteAddr()));
	}

	@Operation(summary = "Renovar la sesión",
			description = "Cambia el refresh token de la cookie por un par nuevo. Cada refresh token sirve una sola "
					+ "vez; reutilizar uno ya usado cierra todas las sesiones del usuario.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Sesión renovada")
	@ApiResponse(responseCode = "401", description = "Sin cookie, o refresh token inválido, expirado o reutilizado",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/refresh")
	ResponseEntity<SesionResponse> refresh(
			@Parameter(hidden = true) @CookieValue(name = CookieDeRefresh.NOMBRE, required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new SesionInvalidaException();
		}
		return conCookie(service.renovar(refreshToken));
	}

	@Operation(summary = "Cerrar sesión",
			description = "Revoca el refresh token de la cookie y la borra. El access token vence solo.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "204", description = "Sesión cerrada; también sin cookie, para que sea idempotente")
	@PostMapping("/logout")
	ResponseEntity<Void> logout(
			@Parameter(hidden = true) @CookieValue(name = CookieDeRefresh.NOMBRE, required = false) String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			service.cerrarSesion(refreshToken);
		}
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.borrada().toString()).build();
	}

	private ResponseEntity<SesionResponse> conCookie(SesionEmitida sesion) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.SET_COOKIE,
						cookie.con(sesion.refreshToken(), sesion.refreshExpiraEn(), clock.instant()).toString())
				.body(SesionResponse.de(sesion));
	}

	/** Los tokens no deben quedarse en la caché de un navegador ni de un proxy. */
	static ResponseEntity<SesionResponse> sinCache(SesionEmitida sesion) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SesionResponse.de(sesion));
	}

}
