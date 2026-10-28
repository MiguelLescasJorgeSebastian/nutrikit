package dev.jorgemiguel.nutrikit.patientaccess.domain;

/** Un dato de configuración del enlace no es válido. Se responde 400 indicando el campo. */
public class EnlaceInvalidoException extends RuntimeException {

	private final String campo;

	public EnlaceInvalidoException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public String getCampo() {
		return campo;
	}

}
