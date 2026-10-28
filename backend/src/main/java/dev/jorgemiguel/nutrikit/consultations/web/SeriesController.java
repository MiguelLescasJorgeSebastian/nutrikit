package dev.jorgemiguel.nutrikit.consultations.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.consultations.application.SeriesDeIndicadores;
import dev.jorgemiguel.nutrikit.consultations.application.SeriesDeIndicadores.Comparacion;
import dev.jorgemiguel.nutrikit.consultations.application.SeriesDeIndicadores.Serie;

/** Evolución de indicadores para gráficas y comparación entre consultas (RF-CON-04, RF-CON-05). */
@Tag(name = "Evolución", description = "Series de indicadores y comparación entre consultas")
@SecurityRequirement(name = "bearer")
@PreAuthorize("hasAnyRole('TITULAR', 'COLABORADOR')")
@RestController
class SeriesController {

	static final String EJEMPLO_SERIES = """
			{
			  "pacienteId": "019a2b3c-4d5e-7f60-8a1b-2c3d4e5f6a7b",
			  "series": [
			    {
			      "indicador": "imc", "nombre": "Índice de masa corporal", "unidad": "kg/m²", "calculado": true,
			      "puntos": [
			        { "fecha": "2026-08-01T16:00:00Z", "consultaId": "019a2b3c-0000-7000-8000-000000000001", "valor": 26.94, "origen": "CALCULADO" },
			        { "fecha": "2026-09-01T16:00:00Z", "consultaId": "019a2b3c-0000-7000-8000-000000000002", "valor": 26.12, "origen": "CALCULADO" }
			      ]
			    },
			    {
			      "indicador": "peso", "nombre": "Peso", "unidad": "kg", "calculado": false,
			      "puntos": [
			        { "fecha": "2026-08-01T16:00:00Z", "consultaId": "019a2b3c-0000-7000-8000-000000000001", "valor": 82.5, "origen": "MANUAL" },
			        { "fecha": "2026-09-01T16:00:00Z", "consultaId": "019a2b3c-0000-7000-8000-000000000002", "valor": 80, "origen": "MANUAL" }
			      ]
			    }
			  ]
			}
			""";

	static final String EJEMPLO_COMPARACION = """
			{
			  "pacienteId": "019a2b3c-4d5e-7f60-8a1b-2c3d4e5f6a7b",
			  "consultaAnterior": "019a2b3c-0000-7000-8000-000000000001", "fechaAnterior": "2026-08-01T16:00:00Z",
			  "consultaPosterior": "019a2b3c-0000-7000-8000-000000000002", "fechaPosterior": "2026-09-01T16:00:00Z",
			  "indicadores": [
			    { "indicador": "peso", "nombre": "Peso", "unidad": "kg", "valorAnterior": 82.5, "valorPosterior": 80,
			      "diferencia": -2.5, "porcentaje": -3.0 },
			    { "indicador": "porcentaje_grasa", "nombre": "Grasa corporal", "unidad": "%", "valorAnterior": 31.2,
			      "valorPosterior": null, "diferencia": null, "porcentaje": null }
			  ]
			}
			""";

	private final SeriesDeIndicadores service;

	SeriesController(SeriesDeIndicadores service) {
		this.service = service;
	}

	@Operation(summary = "Evolución de indicadores del paciente",
			description = "Una serie por indicador, con sus puntos del más antiguo al más reciente, lista para "
					+ "graficarse: el eje X es fecha y el Y valor. Incluye unidad y si cada valor fue medido o calculado.")
	@ApiResponse(responseCode = "200", description = "Series del paciente",
			content = @Content(schema = @Schema(implementation = SeriesResponse.class),
					examples = @ExampleObject(name = "Peso e IMC", value = EJEMPLO_SERIES)))
	@ApiResponse(responseCode = "400", description = "Indicador desconocido, más de 10 o rango de fechas invertido",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "El paciente no existe o está dado de baja",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/api/pacientes/{pacienteId}/series")
	SeriesResponse series(@PathVariable UUID pacienteId,
			@Parameter(description = "Códigos de indicador; sin este parámetro, todos los que tengan datos",
					example = "peso,imc") @RequestParam(required = false) List<String> indicadores,
			@Parameter(description = "Inclusive", example = "2026-01-01")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@Parameter(description = "Inclusive", example = "2026-12-31")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return new SeriesResponse(pacienteId, service.series(pacienteId, indicadores, desde, hasta));
	}

	@Operation(summary = "Comparar dos consultas",
			description = "Diferencia absoluta y porcentual por indicador. Sin importar el orden de los ids, compara la "
					+ "posterior contra la anterior (diferencia = posterior − anterior). Si un indicador falta en alguna "
					+ "de las dos, sus diferencias son null.")
	@ApiResponse(responseCode = "200", description = "Comparación",
			content = @Content(schema = @Schema(implementation = Comparacion.class),
					examples = @ExampleObject(name = "Dos consultas", value = EJEMPLO_COMPARACION)))
	@ApiResponse(responseCode = "400", description = "Son la misma consulta o de pacientes distintos",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Alguna consulta no existe en este consultorio",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/api/consultas/{consultaId}/comparacion/{otraConsultaId}")
	Comparacion comparar(@PathVariable UUID consultaId, @PathVariable UUID otraConsultaId) {
		return service.comparar(consultaId, otraConsultaId);
	}

	@Schema(name = "SeriesResponse")
	record SeriesResponse(UUID pacienteId, List<Serie> series) {
	}

}
