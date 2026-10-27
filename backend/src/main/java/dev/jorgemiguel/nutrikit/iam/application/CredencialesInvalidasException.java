package dev.jorgemiguel.nutrikit.iam.application;

/**
 * Correo o contraseña incorrectos. El mensaje es el mismo en ambos casos (y para usuarios
 * inactivos) para no revelar qué correos están registrados.
 */
public class CredencialesInvalidasException extends RuntimeException {

	public CredencialesInvalidasException() {
		super("Correo o contraseña incorrectos");
	}

}
