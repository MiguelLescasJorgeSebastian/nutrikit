package dev.jorgemiguel.nutrikit.consultations.application;

/** El paciente o la consulta no existe en el consultorio (o el paciente está dado de baja). Se responde 404. */
public class ConsultaNoDisponibleException extends RuntimeException {

	private final String titulo;

	ConsultaNoDisponibleException(String titulo, String mensaje) {
		super(mensaje);
		this.titulo = titulo;
	}

	public String getTitulo() {
		return titulo;
	}

}
