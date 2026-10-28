package dev.jorgemiguel.nutrikit.tenants.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.jorgemiguel.nutrikit.shared.ErrorDeCampo;
import dev.jorgemiguel.nutrikit.tenants.application.ModuloDesconocidoException;
import dev.jorgemiguel.nutrikit.tenants.application.TenantNoEncontradoException;
import dev.jorgemiguel.nutrikit.tenants.domain.ModuloDeNucleoException;
import dev.jorgemiguel.nutrikit.tenants.domain.SlugDuplicadoException;
import dev.jorgemiguel.nutrikit.tenants.domain.SlugInvalidoException;
import dev.jorgemiguel.nutrikit.tenants.domain.TransicionDeEstadoInvalidaException;

/** Traduce las excepciones del módulo a Problem Details (RFC 9457). Solo aplica a este paquete. */
@RestControllerAdvice(basePackageClasses = TenantController.class)
class TenantErroresHandler {

	@ExceptionHandler
	ProblemDetail noEncontrado(TenantNoEncontradoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		problema.setTitle("Consultorio no encontrado");
		return problema;
	}

	/** 409 y no 400: la petición es válida, pero choca con el estado actual de los datos. */
	@ExceptionHandler
	ProblemDetail slugDuplicado(SlugDuplicadoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problema.setTitle("Slug duplicado");
		problema.setProperty("slug", e.getSlug().valor());
		return problema;
	}

	@ExceptionHandler
	ProblemDetail transicionInvalida(TransicionDeEstadoInvalidaException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problema.setTitle("Cambio de estado no permitido");
		problema.setProperty("estadoActual", e.getOrigen());
		problema.setProperty("estadoSolicitado", e.getDestino());
		return problema;
	}

	@ExceptionHandler
	ProblemDetail moduloDeNucleo(ModuloDeNucleoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problema.setTitle("Módulo de núcleo");
		problema.setProperty("modulo", e.getModulo().codigo());
		return problema;
	}

	@ExceptionHandler
	ProblemDetail moduloDesconocido(ModuloDesconocidoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
		problema.setTitle("Módulo no encontrado");
		return problema;
	}

	/** Segunda barrera: normalmente la validación del DTO lo detiene antes. */
	@ExceptionHandler
	ProblemDetail slugInvalido(SlugInvalidoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
		problema.setTitle("Datos inválidos");
		problema.setProperty("errores", List.of(new ErrorDeCampo("slug", e.getMessage())));
		return problema;
	}

}
