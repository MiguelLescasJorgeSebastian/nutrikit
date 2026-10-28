package dev.jorgemiguel.nutrikit.encryption;

/**
 * Un valor cifrado no se pudo descifrar: fue alterado, está truncado, se copió de otro
 * consultorio o falta su llave. Nunca se devuelve basura en su lugar.
 */
public class DatoCifradoInvalidoException extends RuntimeException {

	public DatoCifradoInvalidoException(String mensaje) {
		super(mensaje);
	}

}
