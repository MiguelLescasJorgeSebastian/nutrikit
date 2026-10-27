package dev.jorgemiguel.nutrikit.tenants;

import java.util.UUID;

/**
 * API pública del módulo de consultorios para el resto de los módulos. Expone solo lo que otros
 * necesitan saber, sin la entidad ni el repositorio, que son internos.
 */
public interface Consultorios {

	/** Si existe un consultorio con ese id, en cualquier estado. */
	boolean existe(UUID tenantId);

}
