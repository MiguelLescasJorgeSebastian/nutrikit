package dev.jorgemiguel.nutrikit.tenants.domain;

import java.util.Set;

/**
 * Ciclo de vida de un consultorio.
 *
 * <pre>
 * ACTIVO ⇄ SUSPENDIDO
 *    ↘        ↙
 *    CANCELADO (final)
 * </pre>
 */
public enum EstadoTenant {

	ACTIVO, SUSPENDIDO, CANCELADO;

	/** Estados a los que se puede pasar desde este. Un cancelado no se reactiva. */
	Set<EstadoTenant> siguientesPermitidos() {
		return switch (this) {
			case ACTIVO -> Set.of(SUSPENDIDO, CANCELADO);
			case SUSPENDIDO -> Set.of(ACTIVO, CANCELADO);
			case CANCELADO -> Set.of();
		};
	}

	boolean puedeCambiarA(EstadoTenant destino) {
		return siguientesPermitidos().contains(destino);
	}

}
