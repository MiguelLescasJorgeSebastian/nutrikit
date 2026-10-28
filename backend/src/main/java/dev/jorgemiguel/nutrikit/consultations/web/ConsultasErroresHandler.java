package dev.jorgemiguel.nutrikit.consultations.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.jorgemiguel.nutrikit.consultations.application.ConflictoDeConsultaException;
import dev.jorgemiguel.nutrikit.consultations.application.ConsultaNoDisponibleException;
import dev.jorgemiguel.nutrikit.consultations.domain.MedicionInvalidaException;
import dev.jorgemiguel.nutrikit.shared.ErrorDeCampo;

/** Errores del módulo en Problem Details. Solo aplica a los controladores de este paquete. */
@RestControllerAdvice(basePackageClasses = ConsultasController.class)
class ConsultasErroresHandler {

	@ExceptionHandler
	ProblemDetail medicionInvalida(MedicionInvalidaException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"La petición tiene 1 error(es) de validación");
		problema.setTitle("Datos inválidos");
		problema.setProperty("errores", List.of(new ErrorDeCampo(e.getCampo(), e.getMessage())));
		return problema;
	}

	@ExceptionHandler
	ProblemDetail noDisponible(ConsultaNoDisponibleException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		problema.setTitle(e.getTitulo());
		return problema;
	}

	@ExceptionHandler
	ProblemDetail conflicto(ConflictoDeConsultaException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problema.setTitle(e.getTitulo());
		return problema;
	}

}
