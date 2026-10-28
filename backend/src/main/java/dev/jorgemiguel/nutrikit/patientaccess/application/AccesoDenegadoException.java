package dev.jorgemiguel.nutrikit.patientaccess.application;

import org.springframework.http.HttpStatus;

/**
 * El enlace no da acceso. El estado y el mensaje no dicen más de lo necesario: un enlace
 * inexistente y uno revocado responden igual (404).
 */
public class AccesoDenegadoException extends RuntimeException {

	private final HttpStatus estado;
	private final String titulo;

	private AccesoDenegadoException(HttpStatus estado, String titulo, String mensaje) {
		super(mensaje);
		this.estado = estado;
		this.titulo = titulo;
	}

	static AccesoDenegadoException noDisponible() {
		return new AccesoDenegadoException(HttpStatus.NOT_FOUND, "Enlace no disponible",
				"Este enlace no existe o ya no es válido. Pide uno nuevo a tu nutrióloga.");
	}

	static AccesoDenegadoException caducado() {
		return new AccesoDenegadoException(HttpStatus.GONE, "Enlace caducado",
				"Este enlace ya caducó. Pide uno nuevo a tu nutrióloga.");
	}

	static AccesoDenegadoException pinIncorrecto() {
		return new AccesoDenegadoException(HttpStatus.UNAUTHORIZED, "PIN requerido",
				"Escribe el PIN que te dio tu nutrióloga.");
	}

	public HttpStatus getEstado() {
		return estado;
	}

	public String getTitulo() {
		return titulo;
	}

}
