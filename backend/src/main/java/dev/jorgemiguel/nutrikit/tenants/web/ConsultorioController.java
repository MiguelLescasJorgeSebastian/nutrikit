package dev.jorgemiguel.nutrikit.tenants.web;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.tenants.application.ConsultorioActualService;

/**
 * Datos del consultorio de la petición, identificado por subdominio o por la cabecera
 * {@code X-Tenant} en desarrollo (ver {@link ResolucionTenantFilter}).
 *
 * <p>Exige un JWT del mismo consultorio que la petición.
 */
@Tag(name = "Consultorio actual", description = "Datos del consultorio identificado por subdominio")
@SecurityRequirement(name = "bearer")
@ApiResponse(responseCode = "400", description = "La petición no identifica un consultorio",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "El consultorio no existe o no está activo",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "401", description = "Sin access token o token inválido",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "El token es de otro consultorio",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@RestController
@RequestMapping("/api/consultorio")
class ConsultorioController {

	private static final String CABECERA = "X-Tenant";
	private static final String DESCRIPCION_CABECERA =
			"Slug del consultorio. Solo en desarrollo; en producción se usa el subdominio.";

	private final ConsultorioActualService service;

	ConsultorioController(ConsultorioActualService service) {
		this.service = service;
	}

	@Operation(summary = "Obtener el consultorio actual")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Consultorio de la petición")
	@GetMapping
	TenantResponse obtener() {
		return TenantResponse.de(service.obtener());
	}

	@Operation(summary = "Listar los módulos del consultorio",
			description = "Indica qué módulos están encendidos; el frontend oculta los apagados.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Módulos ordenados por nombre")
	@GetMapping("/modulos")
	List<ModuloResponse> modulos() {
		return service.modulos().stream().map(ModuloResponse::de).toList();
	}

}
