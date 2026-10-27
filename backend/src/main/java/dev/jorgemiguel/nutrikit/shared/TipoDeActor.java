package dev.jorgemiguel.nutrikit.shared;

/** Quién hizo una acción registrada en la bitácora. */
public enum TipoDeActor {

	/** Usuario de un consultorio (nutrióloga, colaboradora, asistente). */
	USUARIO,

	/** Operador de la plataforma. */
	OPERADOR,

	/** Petición sin sesión, por ejemplo un inicio de sesión fallido. */
	ANONIMO,

	/** La aplicación misma, fuera de una petición HTTP (tareas programadas, reintentos). */
	SISTEMA

}
