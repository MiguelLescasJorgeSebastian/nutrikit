package dev.jorgemiguel.nutrikit.consultations.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.consultations.application.IndicadorService;
import dev.jorgemiguel.nutrikit.consultations.domain.Indicador;

/** Catálogo de indicadores. Pertenece al módulo {@code consultations}: si se apaga, responde 404. */
@Tag(name = "Consultas", description = "Consultas, mediciones e indicadores")
@SecurityRequirement(name = "bearer")
@PreAuthorize("hasAnyRole('TITULAR', 'COLABORADOR')")
@RestController
@RequestMapping("/api/indicadores")
class IndicadoresController {

	private final IndicadorService service;

	IndicadoresController(IndicadorService service) {
		this.service = service;
	}

	@Operation(summary = "Catálogo de indicadores", description = "Los globales y los propios del consultorio.")
	@GetMapping
	List<IndicadorResponse> catalogo() {
		return service.catalogo().stream().map(IndicadorResponse::de).toList();
	}

	@Operation(summary = "Agregar un indicador propio",
			description = "Una fila nueva en el catálogo; no requiere cambios en la base. Solo lo ve este consultorio.")
	@ApiResponse(responseCode = "201", description = "Creado")
	@ApiResponse(responseCode = "400", description = "Datos inválidos",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "El código ya existe (global o propio)",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	IndicadorResponse crear(@RequestBody NuevoIndicador nuevo) {
		return IndicadorResponse.de(service.crear(nuevo.codigo(), nuevo.nombre(), nuevo.unidad(), nuevo.valorMinimo(),
				nuevo.valorMaximo()));
	}

	@Schema(name = "NuevoIndicadorRequest")
	record NuevoIndicador(
			@Schema(example = "perimetro_brazo") String codigo,
			@Schema(example = "Perímetro de brazo") String nombre,
			@Schema(example = "cm") String unidad,
			@Schema(description = "Valor mínimo razonable", example = "10") BigDecimal valorMinimo,
			@Schema(description = "Valor máximo razonable", example = "80") BigDecimal valorMaximo) {
	}

	@Schema(name = "IndicadorResponse")
	record IndicadorResponse(UUID id, String codigo, String nombre, String unidad, boolean global, boolean calculado,
			BigDecimal valorMinimo, BigDecimal valorMaximo) {

		static IndicadorResponse de(Indicador i) {
			return new IndicadorResponse(i.getId(), i.getCodigo(), i.getNombre(), i.getUnidad(), i.esGlobal(),
					i.isCalculado(), Indicador.sinCerosSobrantes(i.getValorMinimo()), Indicador.sinCerosSobrantes(i.getValorMaximo()));
		}

	}

}
