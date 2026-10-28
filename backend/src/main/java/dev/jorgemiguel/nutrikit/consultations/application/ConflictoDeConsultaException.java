package dev.jorgemiguel.nutrikit.consultations.application;

/** La operación es válida pero choca con el estado actual. Se responde 409. */
public class ConflictoDeConsultaException extends RuntimeException {

	private final String titulo;

	ConflictoDeConsultaException(String titulo, String mensaje) {
		super(mensaje);
		this.titulo = titulo;
	}

	public String getTitulo() {
		return titulo;
	}

}
