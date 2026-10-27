package dev.jorgemiguel.nutrikit.tenants.domain;

/** Ya existe un consultorio con ese slug; el slug es único en toda la plataforma. */
public class SlugDuplicadoException extends RuntimeException {

	private final Slug slug;

	public SlugDuplicadoException(Slug slug) {
		super("Ya existe un consultorio con el slug '%s'".formatted(slug));
		this.slug = slug;
	}

	public Slug getSlug() {
		return slug;
	}

}
