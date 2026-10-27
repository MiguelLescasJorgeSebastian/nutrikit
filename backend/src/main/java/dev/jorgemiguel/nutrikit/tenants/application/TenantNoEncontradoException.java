package dev.jorgemiguel.nutrikit.tenants.application;

import java.util.UUID;

/** No existe un consultorio con el id solicitado. */
public class TenantNoEncontradoException extends RuntimeException {

	private final UUID id;

	public TenantNoEncontradoException(UUID id) {
		super("No existe un consultorio con id %s".formatted(id));
		this.id = id;
	}

	public UUID getId() {
		return id;
	}

}
