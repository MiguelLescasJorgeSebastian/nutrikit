package dev.jorgemiguel.nutrikit.tenants.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.shared.Origen;
import dev.jorgemiguel.nutrikit.tenants.EstadoDeTenantCambiado;
import dev.jorgemiguel.nutrikit.tenants.TenantCreado;
import dev.jorgemiguel.nutrikit.tenants.domain.EstadoTenant;
import dev.jorgemiguel.nutrikit.tenants.domain.Slug;
import dev.jorgemiguel.nutrikit.tenants.domain.SlugDuplicadoException;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/**
 * Casos de uso de consultorios para el panel de operador. Coordina repositorio y entidad;
 * las reglas de negocio viven en {@link Tenant}.
 */
@Service
@Transactional
public class TenantService {

	private final TenantRepository repository;
	private final ApplicationEventPublisher eventos;

	TenantService(TenantRepository repository, ApplicationEventPublisher eventos) {
		this.repository = repository;
		this.eventos = eventos;
	}

	/**
	 * Da de alta un consultorio. La revisión previa da un error claro en el caso normal; si dos
	 * altas con el mismo slug compiten, el {@code UNIQUE} de la base es la barrera final.
	 */
	public Tenant crear(Slug slug, String nombre) {
		if (repository.existsBySlug(slug)) {
			throw new SlugDuplicadoException(slug);
		}
		Tenant tenant = repository.save(Tenant.crear(slug, nombre));
		// Se publica dentro de la transacción: Modulith guarda el evento junto con el alta, y los
		// listeners corren solo si el alta se confirma.
		eventos.publishEvent(new TenantCreado(tenant.getId(), slug.valor(), Origen.actual(), Instant.now()));
		return tenant;
	}

	@Transactional(readOnly = true)
	public Tenant obtener(UUID id) {
		return buscar(id);
	}

	@Transactional(readOnly = true)
	public Page<Tenant> listar(Pageable pageable) {
		return repository.findAll(pageable);
	}

	/** Lleva el consultorio al estado indicado; la entidad decide si la transición es válida. */
	public Tenant cambiarEstado(UUID id, EstadoTenant destino) {
		Tenant tenant = buscar(id);
		EstadoTenant anterior = tenant.getEstado();
		switch (destino) {
			case ACTIVO -> tenant.reactivar();
			case SUSPENDIDO -> tenant.suspender();
			case CANCELADO -> tenant.cancelar();
		}
		eventos.publishEvent(new EstadoDeTenantCambiado(id, anterior.name(), destino.name(), Origen.actual(),
				Instant.now()));
		return tenant;
	}

	private Tenant buscar(UUID id) {
		return repository.findById(id).orElseThrow(() -> new TenantNoEncontradoException(id));
	}

}
