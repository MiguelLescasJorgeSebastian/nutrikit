package dev.jorgemiguel.nutrikit.tenants.web;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import dev.jorgemiguel.nutrikit.shared.ConsultorioNoDisponibleException;
import dev.jorgemiguel.nutrikit.shared.ConsultorioNoIdentificadoException;
import dev.jorgemiguel.nutrikit.tenants.domain.Slug;
import dev.jorgemiguel.nutrikit.tenants.domain.SlugInvalidoException;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/**
 * Decide a qué consultorio pertenece una petición.
 *
 * <ol>
 * <li>Cabecera {@code X-Tenant}, si está habilitada (solo desarrollo).</li>
 * <li>Subdominio: {@code nutri-ana.nutrikit.mx} → {@code nutri-ana}. Un solo nivel; los
 * subdominios reservados de la plataforma no son consultorios.</li>
 * </ol>
 *
 * <p>El consultorio debe existir y estar activo. Con #8, el consultorio del JWT tendrá que
 * coincidir con el que se resuelve aquí.
 */
class ResolutorDeTenant {

	private static final Set<String> SUBDOMINIOS_RESERVADOS = Set.of("www", "api", "app", "admin");

	private final TenantRepository repository;
	private final TenancyProperties properties;

	ResolutorDeTenant(TenantRepository repository, TenancyProperties properties) {
		this.repository = repository;
		this.properties = properties;
	}

	UUID resolver(HttpServletRequest request) {
		String slug = slugDe(request).orElseThrow(ConsultorioNoIdentificadoException::new);
		return buscarActivo(slug).map(Tenant::getId)
				.orElseThrow(() -> new ConsultorioNoDisponibleException(slug));
	}

	Optional<String> slugDe(HttpServletRequest request) {
		if (properties.cabeceraHabilitada()) {
			String cabecera = request.getHeader(properties.nombreCabecera());
			if (cabecera != null && !cabecera.isBlank()) {
				return Optional.of(cabecera.strip().toLowerCase(Locale.ROOT));
			}
		}
		return subdominio(request.getServerName());
	}

	private Optional<String> subdominio(String host) {
		if (host == null) {
			return Optional.empty();
		}
		String sufijo = "." + properties.dominioBase().toLowerCase(Locale.ROOT);
		String nombre = host.toLowerCase(Locale.ROOT);
		if (!nombre.endsWith(sufijo)) {
			return Optional.empty();
		}
		String etiqueta = nombre.substring(0, nombre.length() - sufijo.length());
		if (etiqueta.isEmpty() || etiqueta.contains(".") || SUBDOMINIOS_RESERVADOS.contains(etiqueta)) {
			return Optional.empty();
		}
		return Optional.of(etiqueta);
	}

	private Optional<Tenant> buscarActivo(String slug) {
		try {
			return repository.findBySlug(Slug.of(slug)).filter(Tenant::estaActivo);
		}
		catch (SlugInvalidoException e) {
			return Optional.empty();
		}
	}

}
