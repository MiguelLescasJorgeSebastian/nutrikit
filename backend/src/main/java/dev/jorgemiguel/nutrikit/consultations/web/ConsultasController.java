package dev.jorgemiguel.nutrikit.consultations.web;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.web.PagedModel;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.jorgemiguel.nutrikit.consultations.application.ConsultaService;
import dev.jorgemiguel.nutrikit.consultations.application.ConsultaService.MedicionPedida;
import dev.jorgemiguel.nutrikit.consultations.domain.Consulta;
import dev.jorgemiguel.nutrikit.consultations.domain.Indicador;
import dev.jorgemiguel.nutrikit.consultations.domain.OrigenMedicion;

/** Consultas del paciente. Información clínica: solo titular y colaboradoras. */
@Tag(name = "Consultas")
@SecurityRequirement(name = "bearer")
@PreAuthorize("hasAnyRole('TITULAR', 'COLABORADOR')")
@ApiResponse(responseCode = "404", description = "El paciente o la consulta no existe en este consultorio",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@RestController
class ConsultasController {

	private static final int TAMANO_MAXIMO = 100;

	private final ConsultaService service;

	ConsultasController(ConsultaService service) {
		this.service = service;
	}

	@Operation(summary = "Registrar una consulta con sus mediciones",
			description = "Cada medición indica el código del indicador; cada indicador va una sola vez.")
	@ApiResponse(responseCode = "201", description = "Registrada")
	@ApiResponse(responseCode = "400", description = "Indicador desconocido o repetido, o valor fuera de rango",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "El paciente no tiene consentimiento vigente",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/api/pacientes/{pacienteId}/consultas")
	ResponseEntity<ConsultaResponse> registrar(@PathVariable UUID pacienteId, @RequestBody NuevaConsulta nueva) {
		List<MedicionPedida> mediciones = nueva.mediciones() == null ? List.of()
				: nueva.mediciones().stream().map(m -> new MedicionPedida(m.indicador(), m.valor())).toList();
		Consulta consulta = service.registrar(pacienteId, nueva.fecha(), nueva.motivo(), nueva.notas(), mediciones);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/consultas/{id}")
				.buildAndExpand(consulta.getId()).toUri();
		return ResponseEntity.created(ubicacion).body(ConsultaResponse.de(consulta, service.indicadoresDe(List.of(consulta))));
	}

	@Operation(summary = "Corregir una medición capturada",
			description = "Cambia su valor y recalcula los indicadores derivados (IMC, cintura-cadera, gasto energético). "
					+ "Los indicadores calculados no se corrigen a mano.")
	@ApiResponse(responseCode = "200", description = "Consulta con la medición corregida y sus derivados recalculados")
	@ApiResponse(responseCode = "400", description = "Indicador calculado o desconocido, o valor fuera de rango",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/api/consultas/{consultaId}/mediciones/{indicador}")
	ConsultaResponse corregir(@PathVariable UUID consultaId, @PathVariable String indicador,
			@RequestBody Correccion correccion) {
		Consulta consulta = service.corregir(consultaId, indicador, correccion.valor());
		return ConsultaResponse.de(consulta, service.indicadoresDe(List.of(consulta)));
	}

	@Operation(summary = "Ver una consulta")
	@GetMapping("/api/consultas/{consultaId}")
	ConsultaResponse obtener(@PathVariable UUID consultaId) {
		Consulta consulta = service.obtener(consultaId);
		return ConsultaResponse.de(consulta, service.indicadoresDe(List.of(consulta)));
	}

	@Operation(summary = "Línea de tiempo del paciente", description = "Sus consultas, de la más reciente a la más antigua.")
	@GetMapping("/api/pacientes/{pacienteId}/consultas")
	PagedModel<ConsultaResponse> lineaDeTiempo(@PathVariable UUID pacienteId,
			@Parameter(example = "0") @RequestParam(defaultValue = "0") int page,
			@Parameter(example = "20") @RequestParam(defaultValue = "20") int size) {
		var pagina = service.lineaDeTiempo(pacienteId, Math.max(page, 0), Math.clamp(size, 1, TAMANO_MAXIMO));
		var indicadores = service.indicadoresDe(pagina.getContent());
		return new PagedModel<>(pagina.map(consulta -> ConsultaResponse.de(consulta, indicadores)));
	}

	@Schema(name = "NuevaConsultaRequest")
	record NuevaConsulta(
			@Schema(description = "Por defecto, ahora", example = "2026-10-27T17:00:00Z") Instant fecha,
			@Schema(example = "Seguimiento mensual") String motivo,
			@Schema(description = "Se guardan cifradas", example = "Buena adherencia al plan") String notas,
			List<NuevaMedicion> mediciones) {

		@Override
		public String toString() {
			return "NuevaConsultaRequest[mediciones=%d]".formatted(mediciones == null ? 0 : mediciones.size());
		}

	}

	@Schema(name = "CorreccionDeMedicion")
	record Correccion(@Schema(example = "71.8") BigDecimal valor) {
	}

	@Schema(name = "NuevaMedicion")
	record NuevaMedicion(@Schema(example = "peso") String indicador, @Schema(example = "72.4") BigDecimal valor) {
	}

	@Schema(name = "ConsultaResponse")
	record ConsultaResponse(UUID id, UUID pacienteId, UUID nutriologoId, Instant fecha, String motivo, String notas,
			List<MedicionResponse> mediciones) {

		static ConsultaResponse de(Consulta consulta, Map<UUID, Indicador> indicadores) {
			return new ConsultaResponse(consulta.getId(), consulta.getPacienteId(), consulta.getNutriologoId(),
					consulta.getFecha(), consulta.getMotivo(), consulta.getNotas(),
					consulta.getMediciones().stream().map(m -> {
						Indicador indicador = indicadores.get(m.getIndicadorId());
						return new MedicionResponse(indicador.getCodigo(), indicador.getNombre(), indicador.getUnidad(),
								Indicador.sinCerosSobrantes(m.getValor()), m.getOrigen());
					}).toList());
		}

		@Override
		public String toString() {
			return "ConsultaResponse[id=%s]".formatted(id);
		}

	}

	@Schema(name = "MedicionResponse")
	record MedicionResponse(String indicador, String nombre, String unidad, BigDecimal valor, OrigenMedicion origen) {
	}

}
