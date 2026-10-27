package dev.jorgemiguel.nutrikit.iam.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.jorgemiguel.nutrikit.iam.application.CredencialesInvalidasException;
import dev.jorgemiguel.nutrikit.iam.application.DemasiadosIntentosException;
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

	/** 429 con {@code Retry-After} en segundos, redondeado hacia arriba. */
	@ExceptionHandler
	ResponseEntity<ProblemDetail> demasiadosIntentos(DemasiadosIntentosException e) {
		long segundos = Math.max(1, (e.getReintentarEn().toMillis() + 999) / 1000);
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
		problema.setTitle("Demasiados intentos");
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, Long.toString(segundos))
				.body(problema);
	}

	@ExceptionHandler
	ProblemDetail emailDuplicado(EmailDuplicadoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problema.setTitle("Correo duplicado");
		return problema;
	}

}
