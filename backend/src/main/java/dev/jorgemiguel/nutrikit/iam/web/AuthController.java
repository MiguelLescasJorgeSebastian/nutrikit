package dev.jorgemiguel.nutrikit.iam.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.iam.application.AutenticacionService;

/**
 * Sesión de los usuarios de un consultorio. El consultorio sale del subdominio (o de
 * {@code X-Tenant} en desarrollo): el mismo correo puede existir en dos consultorios.
 */
@Tag(name = "Autenticación", description = "Inicio de sesión, renovación y cierre de sesión en un consultorio")
@RestController
@RequestMapping("/api/auth")
class AuthController {

	private static final String CABECERA = "X-Tenant";
	private static final String DESCRIPCION_CABECERA =
			"Slug del consultorio. Solo en desarrollo; en producción se usa el subdominio.";

	private final AutenticacionService service;

	AuthController(AutenticacionService service) {
		this.service = service;
	}

	@Operation(summary = "Iniciar sesión", description = "Devuelve un access token de 15 minutos y un refresh token.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Sesión iniciada")
	@ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos; ver Retry-After",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/login")
	ResponseEntity<SesionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return sinCache(service.iniciarSesion(request.email(), request.password(), http.getRemoteAddr()));
	}

	@Operation(summary = "Renovar la sesión",
			description = "Cambia el refresh token por un par nuevo. Cada refresh token sirve una sola vez; "
					+ "reutilizar uno ya usado cierra todas las sesiones del usuario.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Sesión renovada")
	@ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado o reutilizado",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/refresh")
	ResponseEntity<SesionResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return sinCache(service.renovar(request.refreshToken()));
	}

	@Operation(summary = "Cerrar sesión", description = "Revoca el refresh token. El access token vence solo.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "204", description = "Sesión cerrada")
	@PostMapping("/logout")
	ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
		service.cerrarSesion(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	/** Los tokens no deben quedarse en la caché de un navegador ni de un proxy. */
	static ResponseEntity<SesionResponse> sinCache(dev.jorgemiguel.nutrikit.iam.application.SesionEmitida sesion) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SesionResponse.de(sesion));
	}

}
