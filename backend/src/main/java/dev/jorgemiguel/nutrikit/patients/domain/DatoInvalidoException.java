package dev.jorgemiguel.nutrikit.patients.domain;

/** Un dato del paciente no cumple las reglas del dominio. Se responde 400 indicando el campo. */
public class DatoInvalidoException extends RuntimeException {

	private final String campo;

	public DatoInvalidoException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public String getCampo() {
		return campo;
	}

}
