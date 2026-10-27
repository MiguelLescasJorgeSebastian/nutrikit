package dev.jorgemiguel.nutrikit.iam.application;

/** Ya hay un usuario con ese correo en el consultorio. */
public class EmailDuplicadoException extends RuntimeException {

	public EmailDuplicadoException(String email) {
		super("Ya existe un usuario con el correo '%s' en este consultorio".formatted(email));
	}

}
