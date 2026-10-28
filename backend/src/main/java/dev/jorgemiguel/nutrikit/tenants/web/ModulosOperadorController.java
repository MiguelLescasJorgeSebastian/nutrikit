package dev.jorgemiguel.nutrikit.tenants.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.tenants.application.ModulosService;

/** Encender y apagar los módulos de un consultorio. Exige el rol {@code OPERADOR}. */
@Tag(name = "Consultorios")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/operador/tenants/{tenantId}/modulos")
class ModulosOperadorController {

	private final ModulosService service;

	ModulosOperadorController(ModulosService service) {
		this.service = service;
	}

	@Operation(summary = "Listar los módulos de un consultorio")
	@ApiResponse(responseCode = "200", description = "Módulos ordenados por código")
	@ApiResponse(responseCode = "404", description = "El consultorio no existe",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping
	List<ModuloResponse> listar(@PathVariable UUID tenantId) {
		return service.deConsultorio(tenantId).stream().map(ModuloResponse::de).toList();
	}

	/** PUT: fija el estado; repetir la misma petición deja el mismo resultado (idempotente). */
	@Operation(summary = "Encender o apagar un módulo",
			description = "Un módulo apagado deja de responder (404) en ese consultorio; sus datos se conservan.")
	@ApiResponse(responseCode = "200", description = "Estado actualizado")
	@ApiResponse(responseCode = "404", description = "El consultorio o el módulo no existe",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "El módulo es de núcleo y no se puede apagar",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/{modulo}")
	ModuloResponse cambiar(@PathVariable UUID tenantId, @PathVariable String modulo,
			@Valid @RequestBody CambiarModuloRequest request) {
		return ModuloResponse.de(service.cambiar(tenantId, modulo, request.habilitado()));
	}

}
