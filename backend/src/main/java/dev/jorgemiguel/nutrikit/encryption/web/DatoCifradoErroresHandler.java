package dev.jorgemiguel.nutrikit.encryption.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.jorgemiguel.nutrikit.encryption.DatoCifradoInvalidoException;

/**
 * Un dato cifrado que no se puede abrir es un problema de integridad del servidor (alguien alteró
 * la base, o falta una llave), no un error del cliente: 500, sin detalles que ayuden a un atacante.
 *
 * <p>Aplica a toda la API, porque cualquier módulo puede leer campos cifrados. Spring busca el
 * manejador también entre las causas de la excepción, porque Hibernate envuelve las del
 * convertidor.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class DatoCifradoErroresHandler {

	private static final Logger log = LoggerFactory.getLogger(DatoCifradoErroresHandler.class);

	@ExceptionHandler
	ProblemDetail datoCifradoInvalido(DatoCifradoInvalidoException e) {
		log.error("No se pudo descifrar un dato: {}", e.getMessage());
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Un dato protegido no pasó la verificación de integridad. El incidente quedó registrado.");
		problema.setTitle("Dato cifrado inválido");
		return problema;
	}

}
