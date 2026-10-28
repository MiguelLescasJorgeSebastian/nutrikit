package dev.jorgemiguel.nutrikit.iam.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.jorgemiguel.nutrikit.iam.application.CredencialesInvalidasException;
import dev.jorgemiguel.nutrikit.iam.application.EmailDuplicadoException;
import dev.jorgemiguel.nutrikit.iam.application.SesionInvalidaException;

/** Errores del módulo en Problem Details. Solo aplica a los controladores de este paquete. */
@RestControllerAdvice(basePackageClasses = AuthController.class)
class IamErroresHandler {

	@ExceptionHandler
	ProblemDetail credencialesInvalidas(CredencialesInvalidasException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
		problema.setTitle("Credenciales inválidas");
		return problema;
	}

	@ExceptionHandler
	ProblemDetail sesionInvalida(SesionInvalidaException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
		problema.setTitle("Sesión inválida");
		return problema;
	}


	@ExceptionHandler
	ProblemDetail emailDuplicado(EmailDuplicadoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problema.setTitle("Correo duplicado");
		return problema;
	}

}
