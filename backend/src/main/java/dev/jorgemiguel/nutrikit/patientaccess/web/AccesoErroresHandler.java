package dev.jorgemiguel.nutrikit.patientaccess.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.jorgemiguel.nutrikit.patientaccess.application.AccesoDenegadoException;
import dev.jorgemiguel.nutrikit.patientaccess.domain.EnlaceInvalidoException;
import dev.jorgemiguel.nutrikit.shared.ErrorDeCampo;

/** Errores del módulo en Problem Details. Solo aplica a los controladores de este paquete. */
@RestControllerAdvice(basePackageClasses = AccesoController.class)
class AccesoErroresHandler {

	@ExceptionHandler
	ProblemDetail accesoDenegado(AccesoDenegadoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(e.getEstado(), e.getMessage());
		problema.setTitle(e.getTitulo());
		return problema;
	}

	@ExceptionHandler
	ProblemDetail enlaceInvalido(EnlaceInvalidoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"La petición tiene 1 error(es) de validación");
		problema.setTitle("Datos inválidos");
		problema.setProperty("errores", List.of(new ErrorDeCampo(e.getCampo(), e.getMessage())));
		return problema;
	}

}
