package dev.jorgemiguel.nutrikit.patients.web;

import java.time.Instant;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.patients.application.ConsentimientoService;
import dev.jorgemiguel.nutrikit.patients.domain.Consentimiento;

/**
 * Consentimiento de cada paciente. Cualquier rol lo registra (la recepción suele hacerlo), pero
 * nunca se edita ni se borra: otorgar y retirar agregan un hecho al historial.
 */
@Tag(name = "Consentimiento")
@SecurityRequirement(name = "bearer")
@ApiResponse(responseCode = "404", description = "El paciente no existe, es de otro consultorio o está dado de baja",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@RestController
class ConsentimientosController {

	private final ConsentimientoService service;

	ConsentimientosController(ConsentimientoService service) {
		this.service = service;
	}

	@Operation(summary = "Historial de consentimiento del paciente",
			description = "Todos los otorgamientos y retiros en orden, y el estado actual.")
	@GetMapping("/api/pacientes/{pacienteId}/consentimientos")
	Estado historial(@PathVariable UUID pacienteId) {
		List<Consentimiento> hechos = service.historial(pacienteId);
		String versionVigenteDelAviso = service.avisoVigente().map(a -> a.getVersion()).orElse(null);
		Consentimiento ultimo = hechos.isEmpty() ? null : hechos.getLast();
		boolean vigente = ultimo != null && ultimo.estaOtorgado();
		return new Estado(vigente, vigente ? ultimo.getVersionAviso() : null,
				vigente && !ultimo.getVersionAviso().equals(versionVigenteDelAviso),
				hechos.stream().map(Hecho::de).toList());
	}

	@Operation(summary = "Registrar que el paciente aceptó el aviso vigente")
	@ApiResponse(responseCode = "201", description = "Registrado")
	@ApiResponse(responseCode = "409", description = "No hay aviso publicado, o ya aceptó la versión vigente",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/api/pacientes/{pacienteId}/consentimientos")
	@ResponseStatus(HttpStatus.CREATED)
	Hecho otorgar(@PathVariable UUID pacienteId, @RequestBody Registro registro) {
		return Hecho.de(service.otorgar(pacienteId, registro.medio()));
	}

	@Operation(summary = "Registrar que el paciente retiró su consentimiento",
			description = "No borra el otorgamiento: agrega el retiro al historial. Sin consentimiento vigente ya no se "
					+ "puede guardar su historia clínica.")
	@ApiResponse(responseCode = "201", description = "Registrado")
	@ApiResponse(responseCode = "409", description = "No tiene consentimiento vigente",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/api/pacientes/{pacienteId}/consentimientos/retiro")
	@ResponseStatus(HttpStatus.CREATED)
	Hecho retirar(@PathVariable UUID pacienteId, @RequestBody Registro registro) {
		return Hecho.de(service.retirar(pacienteId, registro.medio()));
	}

	@Operation(summary = "Pacientes que aceptaron una versión anterior del aviso",
			description = "Tras publicar una versión nueva: a quiénes pedir que acepten la vigente.")
	@PreAuthorize("hasAnyRole('TITULAR', 'COLABORADOR')")
	@GetMapping("/api/consentimientos/desactualizados")
	List<Hecho> desactualizados() {
		return service.desactualizados().stream().map(Hecho::de).toList();
	}

	@Schema(name = "RegistrarConsentimientoRequest")
	record Registro(@Schema(example = "PRESENCIAL") Consentimiento.Medio medio) {
	}

	@Schema(name = "ConsentimientoResponse")
	record Hecho(UUID id, UUID pacienteId, Consentimiento.Accion accion, String versionAviso, Consentimiento.Medio medio,
			Instant registradoEn, UUID registradoPor) {

		static Hecho de(Consentimiento c) {
			return new Hecho(c.getId(), c.getPacienteId(), c.getAccion(), c.getVersionAviso(), c.getMedio(),
					c.getRegistradoEn(), c.getRegistradoPor());
		}

	}

	@Schema(name = "EstadoDeConsentimientoResponse")
	record Estado(
			@Schema(description = "Si hoy se pueden guardar sus datos de salud") boolean vigente,
			@Schema(nullable = true) String versionAceptada,
			@Schema(description = "Aceptó una versión anterior a la vigente del aviso") boolean desactualizado,
			List<Hecho> historial) {
	}

}
