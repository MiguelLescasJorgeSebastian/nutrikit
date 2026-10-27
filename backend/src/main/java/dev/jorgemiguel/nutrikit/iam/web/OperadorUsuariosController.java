package dev.jorgemiguel.nutrikit.iam.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.iam.application.UsuarioService;

/** Alta de los usuarios de un consultorio; al inicio no hay autorregistro. */
@Tag(name = "Operador")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/operador/tenants/{tenantId}/usuarios")
class OperadorUsuariosController {

	private final UsuarioService service;

	OperadorUsuariosController(UsuarioService service) {
		this.service = service;
	}

	@Operation(summary = "Dar de alta un usuario en un consultorio")
	@ApiResponse(responseCode = "201", description = "Creado")
	@ApiResponse(responseCode = "400", description = "Datos inválidos",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "El consultorio no existe",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Ya hay un usuario con ese correo en el consultorio",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UsuarioResponse crear(@PathVariable UUID tenantId, @Valid @RequestBody CrearUsuarioRequest request) {
		return UsuarioResponse.de(service.registrarEnConsultorio(tenantId, request.email(), request.nombre(),
				request.rol(), request.password()));
	}

}
