package dev.jorgemiguel.nutrikit.tenants.application;

/** El código no corresponde a ningún módulo del catálogo. */
public class ModuloDesconocidoException extends RuntimeException {

	public ModuloDesconocidoException(String codigo) {
		super("No existe el módulo '%s'".formatted(codigo));
	}

}
