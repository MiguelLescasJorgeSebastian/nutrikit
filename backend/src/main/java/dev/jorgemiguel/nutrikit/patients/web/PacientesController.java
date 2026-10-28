package dev.jorgemiguel.nutrikit.patients.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.jorgemiguel.nutrikit.patients.application.PacienteService;
import dev.jorgemiguel.nutrikit.patients.domain.DatoInvalidoException;
import dev.jorgemiguel.nutrikit.patients.domain.DatosPersonales;
import dev.jorgemiguel.nutrikit.patients.domain.Paciente;

/**
 * Pacientes del consultorio de la petición. Pertenece al módulo {@code patients} por su paquete
 * (ver {@code ModuloDelConsultorio}); como es núcleo, nunca se apaga.
 */
@Tag(name = "Pacientes", description = "Alta, edición, búsqueda y baja de pacientes")
@SecurityRequirement(name = "bearer")
@ApiResponse(responseCode = "401", description = "Sin access token o token inválido",
		content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@RestController
@RequestMapping("/api/pacientes")
class PacientesController {

	private static final String CABECERA = "X-Tenant";
	private static final String DESCRIPCION_CABECERA =
			"Slug del consultorio. Solo en desarrollo; en producción se usa el subdominio.";

	private final PacienteService service;

	PacientesController(PacienteService service) {
		this.service = service;
	}

	@Operation(summary = "Dar de alta un paciente")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "201", description = "Creado; la cabecera Location apunta al paciente")
	@ApiResponse(responseCode = "400", description = "Datos inválidos, con el detalle por campo",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	ResponseEntity<PacienteResponse> registrar(@Valid @RequestBody PacienteRequest request) {
		Paciente paciente = service.registrar(datos(request));
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(paciente.getId())
				.toUri();
		return ResponseEntity.created(ubicacion).body(respuesta(paciente));
	}

	@Operation(summary = "Obtener un paciente")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Encontrado")
	@ApiResponse(responseCode = "404", description = "No existe, es de otro consultorio o está dado de baja",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/{id}")
	PacienteResponse obtener(@PathVariable UUID id) {
		return respuesta(service.obtener(id));
	}

	@Operation(summary = "Buscar pacientes",
			description = "Por nombre, apellidos o teléfono, sin importar acentos ni mayúsculas. Cada palabra debe "
					+ "aparecer: \"jose per\" encuentra a \"José Pérez\". Sin q, lista todos. Orden por defecto: "
					+ "apellidos y nombre.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Página de pacientes activos")
	@GetMapping
	PagedModel<PacienteResponse> buscar(
			@Parameter(description = "Texto a buscar", example = "jose") @RequestParam(required = false) String q,
			@ParameterObject @PageableDefault(size = 20, sort = { "apellidos", "nombre", "id" },
					direction = Sort.Direction.ASC) Pageable pageable) {
		LocalDate hoy = service.hoy();
		return new PagedModel<>(service.buscar(q, pageable).map(paciente -> PacienteResponse.de(paciente, hoy)));
	}

	@Operation(summary = "Editar los datos generales",
			description = "Reemplaza todos los datos generales. Envía la versión que leíste: si alguien editó antes, "
					+ "responde 409 y no se pisa su cambio.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "200", description = "Actualizado; la respuesta trae la versión nueva")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o falta la versión",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Alguien más editó al paciente",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PutMapping("/{id}")
	PacienteResponse actualizar(@PathVariable UUID id, @Valid @RequestBody PacienteRequest request) {
		if (request.version() == null) {
			throw new DatoInvalidoException("version", "es obligatoria al editar");
		}
		return respuesta(service.actualizar(id, request.version(), datos(request)));
	}

	/** Solo titular y colaboradoras: una asistente no da de baja expedientes. */
	@Operation(summary = "Dar de baja un paciente",
			description = "Baja lógica: deja de aparecer en listados y búsquedas, pero su expediente se conserva.")
	@Parameter(name = CABECERA, in = ParameterIn.HEADER, example = "nutri-ana", description = DESCRIPCION_CABECERA)
	@ApiResponse(responseCode = "204", description = "Dado de baja")
	@ApiResponse(responseCode = "403", description = "Tu rol no puede dar de baja pacientes",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No existe o ya estaba dado de baja",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	@PreAuthorize("hasAnyRole('TITULAR', 'COLABORADOR')")
	@DeleteMapping("/{id}")
	ResponseEntity<Void> darDeBaja(@PathVariable UUID id) {
		service.darDeBaja(id);
		return ResponseEntity.noContent().build();
	}

	private DatosPersonales datos(PacienteRequest request) {
		return DatosPersonales.de(request.nombre(), request.apellidos(), request.fechaNacimiento(), request.sexo(),
				request.telefono(), request.email(), request.ocupacion(), service.hoy());
	}

	private PacienteResponse respuesta(Paciente paciente) {
		return PacienteResponse.de(paciente, service.hoy());
	}

}
