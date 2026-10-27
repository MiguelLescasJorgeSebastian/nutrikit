package dev.jorgemiguel.nutrikit.shared;

/**
 * El consultorio indicado no existe o no está activo. Es la misma respuesta en ambos casos para
 * no revelar qué consultorios existen ni cuáles están suspendidos.
 */
public class ConsultorioNoDisponibleException extends RuntimeException {

	private final String slug;

	public ConsultorioNoDisponibleException(String slug) {
		super("El consultorio '%s' no existe o no está disponible".formatted(slug));
		this.slug = slug;
	}

	public String getSlug() {
		return slug;
	}

}
