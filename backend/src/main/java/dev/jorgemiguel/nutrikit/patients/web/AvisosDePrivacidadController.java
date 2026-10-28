package dev.jorgemiguel.nutrikit.patients.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.jorgemiguel.nutrikit.patients.application.ConsentimientoService;
import dev.jorgemiguel.nutrikit.patients.domain.AvisoDePrivacidad;

/** Aviso de privacidad del consultorio. Publicarlo es decisión de la titular, responsable de los datos. */
@Tag(name = "Consentimiento", description = "Aviso de privacidad y consentimiento de los pacientes (LFPDPPP)")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/avisos-de-privacidad")
class AvisosDePrivacidadController {

	private final ConsentimientoService service;

	AvisosDePrivacidadController(ConsentimientoService service) {
		this.service = service;
	}

	@Operation(summary = "Publicar una versión del aviso de privacidad",
			description = "La nueva versión pasa a ser la vigente. Una versión publicada no se modifica.")
	@ApiResponse(responseCode = "201", description = "Publicada")
	@ApiResponse(responseCode = "403", description = "Solo la titular publica el aviso",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Ya existe esa versión",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PreAuthorize("hasRole('TITULAR')")
	@PostMapping
	ResponseEntity<Aviso> publicar(@RequestBody Publicacion publicacion) {
		AvisoDePrivacidad aviso = service.publicarAviso(publicacion.version(), publicacion.contenido());
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/vigente").build().toUri();
		return ResponseEntity.created(ubicacion).body(Aviso.de(aviso));
	}

	@Operation(summary = "Versión vigente del aviso")
	@ApiResponse(responseCode = "200", description = "La última versión publicada")
	@ApiResponse(responseCode = "404", description = "El consultorio no ha publicado un aviso",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/vigente")
	Aviso vigente() {
		return service.avisoVigente().map(Aviso::de).orElseThrow(AvisoNoPublicadoException::new);
	}

	@Operation(summary = "Todas las versiones publicadas, de la más reciente a la más antigua")
	@GetMapping
	List<Aviso> versiones() {
		return service.avisos().stream().map(Aviso::de).toList();
	}

	@Schema(name = "PublicarAvisoRequest")
	record Publicacion(@Schema(example = "2026.1") String version,
			@Schema(example = "Consultorio Ana López, con domicilio en..., es responsable del tratamiento...") String contenido) {
	}

	@Schema(name = "AvisoDePrivacidadResponse")
	record Aviso(String version, String contenido, Instant publicadoEn) {

		static Aviso de(AvisoDePrivacidad aviso) {
			return new Aviso(aviso.getVersion(), aviso.getContenido(), aviso.getPublicadoEn());
		}

	}

	/** 404 sin aviso publicado. */
	static class AvisoNoPublicadoException extends RuntimeException {

		AvisoNoPublicadoException() {
			super("El consultorio no ha publicado un aviso de privacidad");
		}

	}

}
