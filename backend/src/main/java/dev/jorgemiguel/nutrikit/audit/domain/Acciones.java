package dev.jorgemiguel.nutrikit.audit.domain;

/** Nombres de acciones y recursos que se guardan en la bitácora. */
public final class Acciones {

	public static final String TENANT_CREADO = "TENANT_CREADO";
	public static final String TENANT_ESTADO_CAMBIADO = "TENANT_ESTADO_CAMBIADO";
	public static final String LOGIN_EXITOSO = "LOGIN_EXITOSO";
	public static final String LOGIN_FALLIDO = "LOGIN_FALLIDO";
	public static final String MODULO_ENCENDIDO = "MODULO_ENCENDIDO";
	public static final String MODULO_APAGADO = "MODULO_APAGADO";
	public static final String PACIENTE_CREADO = "PACIENTE_CREADO";
	public static final String PACIENTE_ACTUALIZADO = "PACIENTE_ACTUALIZADO";
	public static final String PACIENTE_DADO_DE_BAJA = "PACIENTE_DADO_DE_BAJA";

	public static final String RECURSO_TENANT = "TENANT";
	public static final String RECURSO_USUARIO = "USUARIO";
	public static final String RECURSO_PACIENTE = "PACIENTE";

	/** La tabla no tiene columna de detalle: el módulo va en el tipo de recurso ({@code MODULO_SCHEDULING}). */
	public static String recursoModulo(String codigo) {
		return "MODULO_" + codigo.toUpperCase(java.util.Locale.ROOT);
	}

	private Acciones() {
	}

}
