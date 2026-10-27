package dev.jorgemiguel.nutrikit.iam.application;

/**
 * Claims propios del access token. Solo identificadores: el JWT se puede decodificar sin la
 * clave, así que nunca lleva datos personales ni de salud.
 */
public final class ClaimsJwt {

	/** Consultorio del usuario; ausente en el token del operador. */
	public static final String TENANT_ID = "tenant_id";

	/** Rol: TITULAR, COLABORADOR, ASISTENTE u OPERADOR. */
	public static final String ROL = "rol";

	/** Rol de la plataforma; no existe en la tabla usuario. */
	public static final String ROL_OPERADOR = "OPERADOR";

	private ClaimsJwt() {
	}

}
