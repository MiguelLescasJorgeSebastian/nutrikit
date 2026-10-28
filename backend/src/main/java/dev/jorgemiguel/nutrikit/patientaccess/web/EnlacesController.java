package dev.jorgemiguel.nutrikit.patientaccess.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.patientaccess.application.EnlacesService;
import dev.jorgemiguel.nutrikit.patientaccess.domain.EnlaceDeAcceso;

/** Enlace privado de un paciente, desde el panel. Da acceso a datos de salud: titular y colaboradoras. */
@Tag(name = "Página del paciente")
@SecurityRequirement(name = "bearer")
@PreAuthorize("hasAnyRole('TITULAR', 'COLABORADOR')")
@ApiResponse(responseCode = "404", description = "El paciente no existe o no tiene enlace vigente",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@RestController
@RequestMapping("/api/pacientes/{pacienteId}/enlace")
class EnlacesController {

	private final EnlacesService service;

	EnlacesController(EnlacesService service) {
		this.service = service;
	}

	@Operation(summary = "Generar (o regenerar) el enlace",
			description = "Revoca el anterior al instante. El token completo se devuelve solo en esta respuesta; "
					+ "después no se puede recuperar, solo regenerar.")
	@ApiResponse(responseCode = "201", description = "Enlace nuevo con su token")
	@PostMapping
	ResponseEntity<EnlaceNuevo> generar(@PathVariable UUID pacienteId, @RequestBody(required = false) Configuracion config) {
		var c = config == null ? new Configuracion(null, null) : config;
		var emitido = service.generar(pacienteId, c.pin(), c.expiraEn());
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
				.body(new EnlaceNuevo(emitido.token(), "/api/acceso/" + emitido.token(), Estado.de(emitido.enlace())));
	}

	@Operation(summary = "Estado del enlace vigente", description = "Sin el token: no se puede volver a mostrar.")
	@GetMapping
	Estado estado(@PathVariable UUID pacienteId) {
		return service.vigente(pacienteId).map(Estado::de).orElse(Estado.SIN_ENLACE);
	}

	@Operation(summary = "Cambiar PIN y caducidad", description = "Sin cambiar el token. null quita el PIN o la caducidad.")
	@PutMapping
	Estado configurar(@PathVariable UUID pacienteId, @RequestBody Configuracion config) {
		return Estado.de(service.configurar(pacienteId, config.pin(), config.expiraEn()));
	}

	@Operation(summary = "Revocar el enlace", description = "Deja de servir al instante.")
	@ApiResponse(responseCode = "204", description = "Revocado (o no había enlace vigente)")
	@DeleteMapping
	ResponseEntity<Void> revocar(@PathVariable UUID pacienteId) {
		service.revocar(pacienteId);
		return ResponseEntity.noContent().build();
	}

	@Schema(name = "ConfiguracionDeEnlace")
	record Configuracion(@Schema(description = "4 a 8 dígitos; null = sin PIN", example = "4821") String pin,
			@Schema(description = "Fecha futura; null = no caduca", example = "2027-01-01T00:00:00Z") Instant expiraEn) {

		@Override
		public String toString() {
			return "ConfiguracionDeEnlace[pin=****, expiraEn=%s]".formatted(expiraEn);
		}

	}

	@Schema(name = "EnlaceNuevo")
	record EnlaceNuevo(@Schema(description = "Se muestra una sola vez") String token, String rutaApi, Estado estado) {

		@Override
		public String toString() {
			return "EnlaceNuevo[****]";
		}

	}

	@Schema(name = "EstadoDeEnlace")
	record Estado(boolean vigente, boolean conPin, Instant expiraEn, Instant creadoEn, Instant ultimoUsoEn) {

		static final Estado SIN_ENLACE = new Estado(false, false, null, null, null);

		static Estado de(EnlaceDeAcceso enlace) {
			return new Estado(!enlace.estaRevocado(), enlace.tienePin(), enlace.getExpiraEn(), enlace.getCreadoEn(),
					enlace.getUltimoUsoEn());
		}

	}

}
