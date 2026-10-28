package dev.jorgemiguel.nutrikit.tenants.domain;

import dev.jorgemiguel.nutrikit.tenants.ModuloDelConsultorio;

/** Se intentó apagar un módulo de núcleo, que está siempre encendido. */
public class ModuloDeNucleoException extends RuntimeException {

	private final ModuloDelConsultorio modulo;

	public ModuloDeNucleoException(ModuloDelConsultorio modulo) {
		super("El módulo '%s' es parte del núcleo y no se puede apagar".formatted(modulo.codigo()));
		this.modulo = modulo;
	}

	public ModuloDelConsultorio getModulo() {
		return modulo;
	}

}
