package dev.jorgemiguel.nutrikit.iam.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.iam.application.OperadorAutenticacionService;

@Tag(name = "Operador", description = "Administración de la plataforma")
@RestController
@RequestMapping("/api/operador/auth")
class OperadorAuthController {

	private final OperadorAutenticacionService service;

	OperadorAuthController(OperadorAutenticacionService service) {
		this.service = service;
	}

	@Operation(summary = "Iniciar sesión como operador",
			description = "Devuelve solo un access token de 15 minutos; al vencer se inicia sesión de nuevo.")
	@ApiResponse(responseCode = "200", description = "Sesión iniciada")
	@ApiResponse(responseCode = "401", description = "Credenciales incorrectas",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos; ver Retry-After",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/login")
	ResponseEntity<SesionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return AuthController.sinCache(service.iniciarSesion(request.email(), request.password(), http.getRemoteAddr()));
	}

}
