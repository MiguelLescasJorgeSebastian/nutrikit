package dev.jorgemiguel.nutrikit.tenants.web;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.jorgemiguel.nutrikit.tenants.application.TenantService;
import dev.jorgemiguel.nutrikit.tenants.domain.Slug;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;

/**
 * Alta y administración de consultorios desde el panel de operador.
 *
 * <p>Seguridad: {@code /api/operador/**} queda abierto mientras llega #8; ahí debe exigir el rol
 * de operador.
 */
@Tag(name = "Consultorios", description = "Alta y administración de consultorios (panel de operador)")
@RestController
@RequestMapping(TenantController.RUTA)
class TenantController {

	static final String RUTA = "/api/operador/tenants";

	private final TenantService service;

	TenantController(TenantService service) {
		this.service = service;
	}

	@Operation(summary = "Dar de alta un consultorio", description = "El consultorio nace en estado ACTIVO.")
	@ApiResponse(responseCode = "201", description = "Creado; la cabecera Location apunta al nuevo recurso")
	@ApiResponse(responseCode = "400", description = "Cuerpo inválido; el detalle viene por campo en `errores`",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Ya existe un consultorio con ese slug",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	ResponseEntity<TenantResponse> crear(@Valid @RequestBody CrearTenantRequest request) {
		Tenant tenant = service.crear(Slug.of(request.slug()), request.nombre());
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(tenant.getId())
				.toUri();
		return ResponseEntity.created(ubicacion).body(TenantResponse.de(tenant));
	}

	@Operation(summary = "Obtener un consultorio")
	@ApiResponse(responseCode = "200", description = "Encontrado")
	@ApiResponse(responseCode = "404", description = "No existe",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/{id}")
	TenantResponse obtener(@PathVariable UUID id) {
		return TenantResponse.de(service.obtener(id));
	}

	@Operation(summary = "Listar consultorios",
			description = "Paginado. Por defecto, 20 por página, del más reciente al más antiguo. Máximo 100 por página.")
	@ApiResponse(responseCode = "200", description = "Página de consultorios")
	@ApiResponse(responseCode = "400", description = "Parámetro de orden inválido",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping
	PagedModel<TenantResponse> listar(
			// id desempata: con UUIDv7 también es cronológico y, al ser único, ningún registro se repite
			// ni se pierde entre páginas.
			@ParameterObject @PageableDefault(size = 20, sort = { "creadoEn", "id" }, direction = Sort.Direction.DESC)
			Pageable pageable) {
		return new PagedModel<>(service.listar(pageable).map(TenantResponse::de));
	}

	@Operation(summary = "Cambiar el estado de un consultorio",
			description = "ACTIVO ⇄ SUSPENDIDO; ambos pueden pasar a CANCELADO, que es final.")
	@ApiResponse(responseCode = "200", description = "Estado actualizado")
	@ApiResponse(responseCode = "400", description = "Estado ausente o desconocido",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "La transición no está permitida desde el estado actual",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PatchMapping("/{id}/estado")
	TenantResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambiarEstadoRequest request) {
		return TenantResponse.de(service.cambiarEstado(id, request.estado()));
	}

}
