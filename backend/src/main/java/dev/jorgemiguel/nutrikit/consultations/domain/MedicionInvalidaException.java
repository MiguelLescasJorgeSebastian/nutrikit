package dev.jorgemiguel.nutrikit.consultations.domain;

/** Un dato de la consulta o de un indicador no es válido. Se responde 400 indicando el campo. */
public class MedicionInvalidaException extends RuntimeException {

	private final String campo;

	public MedicionInvalidaException(String campo, String mensaje) {
		super(mensaje);
		this.campo = campo;
	}

	public String getCampo() {
		return campo;
	}

}
