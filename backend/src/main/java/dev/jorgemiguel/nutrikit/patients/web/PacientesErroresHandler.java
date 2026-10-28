package dev.jorgemiguel.nutrikit.patients.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.jorgemiguel.nutrikit.patients.application.ConflictoDeConsentimientoException;
import dev.jorgemiguel.nutrikit.patients.application.PacienteNoEncontradoException;
import dev.jorgemiguel.nutrikit.patients.application.VersionDesactualizadaException;
import dev.jorgemiguel.nutrikit.patients.domain.DatoInvalidoException;
import dev.jorgemiguel.nutrikit.shared.ErrorDeCampo;

/** Errores del módulo en Problem Details. Solo aplica a los controladores de este paquete. */
@RestControllerAdvice(basePackageClasses = PacientesController.class)
class PacientesErroresHandler {

	@ExceptionHandler
	ProblemDetail datoInvalido(DatoInvalidoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"La petición tiene 1 error(es) de validación");
		problema.setTitle("Datos inválidos");
		problema.setProperty("errores", List.of(new ErrorDeCampo(e.getCampo(), e.getMessage())));
		return problema;
	}

	@ExceptionHandler
	ProblemDetail noEncontrado(PacienteNoEncontradoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		problema.setTitle("Paciente no encontrado");
		return problema;
	}

	@ExceptionHandler
	ProblemDetail conflictoDeConsentimiento(ConflictoDeConsentimientoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problema.setTitle(e.getTitulo());
		return problema;
	}

	@ExceptionHandler
	ProblemDetail avisoNoPublicado(AvisosDePrivacidadController.AvisoNoPublicadoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		problema.setTitle("Sin aviso de privacidad");
		return problema;
	}

	@ExceptionHandler
	ProblemDetail versionDesactualizada(VersionDesactualizadaException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problema.setTitle("Edición en conflicto");
		problema.setProperty("versionActual", e.getVersionActual());
		return problema;
	}

}
