package dev.jorgemiguel.nutrikit.iam.application;

/**
 * El refresh token no sirve: no existe, expiró, ya se usó o el usuario está inactivo. Un solo
 * mensaje para todos los casos; el cliente debe volver a iniciar sesión.
 */
public class SesionInvalidaException extends RuntimeException {

	public SesionInvalidaException() {
		super("La sesión no es válida. Inicia sesión de nuevo.");
	}

}
