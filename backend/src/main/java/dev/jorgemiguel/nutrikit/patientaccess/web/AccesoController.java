package dev.jorgemiguel.nutrikit.patientaccess.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import dev.jorgemiguel.nutrikit.consultations.ConsultasDelPaciente;
import dev.jorgemiguel.nutrikit.patientaccess.application.AccesoDelPacienteService;
import dev.jorgemiguel.nutrikit.patientaccess.application.AccesoDelPacienteService.PaginaDelPaciente;

/**
 * Página del paciente, pública: la credencial es el enlace. El token va en la ruta y no en un
 * parámetro de consulta, que más herramientas registran (logs de proxies, analítica); el PIN va en
 * una cabecera, nunca en la URL.
 */
@Tag(name = "Página del paciente", description = "Acceso del paciente con su enlace privado, sin cuenta")
@RestController
class AccesoController {

	private final AccesoDelPacienteService service;

	AccesoController(AccesoDelPacienteService service) {
		this.service = service;
	}

	@Operation(summary = "Abrir la página del paciente",
			description = "En el subdominio de su consultorio. Si el enlace tiene PIN, va en la cabecera X-Pin.")
	@Parameter(name = "X-Tenant", in = ParameterIn.HEADER, example = "nutri-ana",
			description = "Slug del consultorio. Solo en desarrollo; en producción se usa el subdominio.")
	@ApiResponse(responseCode = "200", description = "Datos del paciente")
	@ApiResponse(responseCode = "401", description = "Falta el PIN o es incorrecto",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "El enlace no existe o fue revocado",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "410", description = "El enlace caducó",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos; ver Retry-After",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/api/acceso/{token}")
	ResponseEntity<Pagina> abrir(@PathVariable String token,
			@Parameter(description = "PIN, si el enlace lo tiene", example = "4821") @RequestHeader(name = "X-Pin", required = false) String pin,
			HttpServletRequest peticion) {
		PaginaDelPaciente pagina = service.abrir(token, pin, peticion.getRemoteAddr());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Pagina.de(pagina));
	}

	@Schema(name = "PaginaDelPaciente")
	record Pagina(String nombre, String apellidos, Consulta ultimaConsulta, Serie peso) {

		static Pagina de(PaginaDelPaciente pagina) {
			var ultima = pagina.ultimaConsulta().map(c -> new Consulta(c.fecha(),
					c.mediciones().stream().map(m -> new Valor(m.indicador(), m.nombre(), m.unidad(), m.valor())).toList()))
					.orElse(null);
			ConsultasDelPaciente.SerieDeEvolucion peso = pagina.peso();
			return new Pagina(pagina.paciente().nombre(), pagina.paciente().apellidos(), ultima,
					new Serie(peso.unidad(), peso.puntos().stream().map(p -> new Punto(p.fecha(), p.valor())).toList()));
		}

		/** Datos personales: que nunca terminen en un log. */
		@Override
		public String toString() {
			return "PaginaDelPaciente[****]";
		}

	}

	record Consulta(Instant fecha, List<Valor> mediciones) {
	}

	record Valor(String indicador, String nombre, String unidad, BigDecimal valor) {
	}

	record Serie(String unidad, List<Punto> puntos) {
	}

	record Punto(Instant fecha, BigDecimal valor) {
	}

}
