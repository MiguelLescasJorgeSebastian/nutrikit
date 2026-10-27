package dev.jorgemiguel.nutrikit.shared.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import dev.jorgemiguel.nutrikit.shared.ErrorDeCampo;

/**
 * Formato de errores común a toda la API: Problem Details (RFC 9457).
 *
 * <p>{@link ResponseEntityExceptionHandler} ya convierte las excepciones de Spring MVC (JSON mal
 * formado, método no permitido, ruta inexistente...). Aquí se agrega el detalle por campo a los
 * 400 de validación y se cubren errores de datos que no son de ningún módulo en particular. Las
 * excepciones de dominio las traduce cada módulo en su propio {@code @RestControllerAdvice}.
 */
@RestControllerAdvice
class ManejadorErroresApi extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ErrorDeCampo> errores = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new ErrorDeCampo(error.getField(), error.getDefaultMessage()))
				.sorted(Comparator.comparing(ErrorDeCampo::campo).thenComparing(ErrorDeCampo::mensaje))
				.toList();

		ProblemDetail problema = ex.getBody();
		problema.setTitle("Datos inválidos");
		problema.setDetail("La petición tiene %d error(es) de validación".formatted(errores.size()));
		problema.setProperty("errores", errores);
		return handleExceptionInternal(ex, problema, headers, status, request);
	}

	/** Orden por una propiedad que no existe, por ejemplo {@code ?sort=contrasena}. */
	@ExceptionHandler
	ProblemDetail ordenInvalido(PropertyReferenceException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"No se puede ordenar por '%s'".formatted(e.getPropertyName()));
		problema.setTitle("Parámetro de orden inválido");
		return problema;
	}

	/**
	 * Restricción de la base violada (por ejemplo, un {@code UNIQUE} cuando dos peticiones
	 * compiten). No se expone el mensaje de la base: revelaría nombres de tablas y valores.
	 */
	@ExceptionHandler
	ProblemDetail conflictoDeDatos(DataIntegrityViolationException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"La operación choca con datos existentes");
		problema.setTitle("Conflicto de datos");
		return problema;
	}

}
