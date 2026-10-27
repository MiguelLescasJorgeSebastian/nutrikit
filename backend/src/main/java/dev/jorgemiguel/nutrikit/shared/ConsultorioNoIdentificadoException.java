package dev.jorgemiguel.nutrikit.shared;

/** La petición necesita un consultorio y no indica cuál (ni subdominio ni cabecera). */
public class ConsultorioNoIdentificadoException extends RuntimeException {

	public ConsultorioNoIdentificadoException() {
		super("La petición no identifica un consultorio. Usa el subdominio del consultorio "
				+ "({slug}.nutrikit.mx) o, en desarrollo, la cabecera X-Tenant.");
	}

}
