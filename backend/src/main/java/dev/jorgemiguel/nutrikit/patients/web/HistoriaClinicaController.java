package dev.jorgemiguel.nutrikit.patients.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.patients.application.HistoriaClinicaService;
import dev.jorgemiguel.nutrikit.patients.domain.ContenidoClinico;
import dev.jorgemiguel.nutrikit.patients.domain.HistoriaClinica;

/**
 * Historia clínica del paciente. Información clínica: solo titular y colaboradoras; una asistente
 * no la ve (ver {@code RolUsuario.ASISTENTE}).
 */
@Tag(name = "Historia clínica", description = "Antecedentes, padecimientos, medicamentos y alergias, cifrados en la base")
@SecurityRequirement(name = "bearer")
@ApiResponse(responseCode = "403", description = "Tu rol no puede ver información clínica",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "El paciente no existe, es de otro consultorio o está dado de baja",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@PreAuthorize("hasAnyRole('TITULAR', 'COLABORADOR')")
@RestController
@RequestMapping("/api/pacientes/{pacienteId}/historia-clinica")
class HistoriaClinicaController {

	private static final String CABECERA = "X-Tenant";
	private static final String DESCRIPCION_CABECERA =
			"Slug del consultorio. Solo en desarrollo; en producción se usa el subdominio.";

	private final HistoriaClinicaService service;

	HistoriaClinicaController(HistoriaClinicaService service) {
		this.service = service;
	}

	@Operation(summary = "Consultar la historia clínica",
			description = "Si el paciente aún no tiene, todos los campos vienen vacíos y version es null. Cada consulta "
					+ "queda en la bitácora.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Historia clínica en claro")
	@GetMapping
	Respuesta consultar(@PathVariable UUID pacienteId) {
		return service.consultar(pacienteId).map(Respuesta::de).orElseGet(() -> Respuesta.vacia(pacienteId));
	}

	@Operation(summary = "Guardar la historia clínica",
			description = "Reemplaza todo el contenido. Para editar una existente envía la versión que leíste; si alguien "
					+ "la cambió antes, responde 409.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Guardada; trae la versión nueva")
	@ApiResponse(responseCode = "400", description = "Datos inválidos",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Alguien más la editó",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping
	Respuesta guardar(@PathVariable UUID pacienteId, @RequestBody Peticion peticion) {
		var contenido = new ContenidoClinico(peticion.antecedentes(), peticion.padecimientos(), peticion.medicamentos(),
				peticion.alergias(), peticion.camposExtra());
		return Respuesta.de(service.guardar(pacienteId, peticion.version(), contenido));
	}

	@Schema(name = "HistoriaClinicaRequest", description = "Contenido completo de la historia clínica")
	record Peticion(
			@Schema(example = "Madre con diabetes tipo 2") String antecedentes,
			@Schema(example = "Hipotiroidismo") String padecimientos,
			@Schema(example = "Levotiroxina 50 mcg") String medicamentos,
			@Schema(example = "Penicilina") String alergias,
			@Schema(description = "Secciones propias del consultorio", example = "{\"Hábitos de sueño\": \"6 horas\"}")
			Map<String, String> camposExtra,
			@Schema(description = "Obligatoria si ya existe una historia", example = "0") Long version) {

		@Override
		public String toString() {
			return "HistoriaClinicaRequest[version=%s]".formatted(version);
		}

	}

	@Schema(name = "HistoriaClinicaResponse")
	record Respuesta(UUID pacienteId, String antecedentes, String padecimientos, String medicamentos, String alergias,
			Map<String, String> camposExtra, Instant actualizadoEn,
			@Schema(nullable = true, description = "null si aún no existe") Long version) {

		static Respuesta de(HistoriaClinica historia) {
			var c = historia.contenido();
			return new Respuesta(historia.getPacienteId(), c.antecedentes(), c.padecimientos(), c.medicamentos(),
					c.alergias(), c.camposExtra(), historia.getActualizadoEn(), historia.getVersion());
		}

		static Respuesta vacia(UUID pacienteId) {
			return new Respuesta(pacienteId, null, null, null, null, Map.of(), null, null);
		}

		@Override
		public String toString() {
			return "HistoriaClinicaResponse[pacienteId=%s, version=%s]".formatted(pacienteId, version);
		}

	}

}
