package dev.jorgemiguel.nutrikit.iam.domain;

/** Roles dentro de un consultorio (modelo de datos, página 3). */
public enum RolUsuario {

	/** Dueña del consultorio: todo, incluida la configuración y los usuarios. */
	TITULAR,

	/** Otra nutrióloga del consultorio: trabajo clínico. */
	COLABORADOR,

	/** Apoyo administrativo: agenda y datos de contacto, sin información clínica. */
	ASISTENTE

}
