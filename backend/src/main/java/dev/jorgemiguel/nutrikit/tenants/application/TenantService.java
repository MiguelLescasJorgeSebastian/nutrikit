package dev.jorgemiguel.nutrikit.tenants.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	TenantService(TenantRepository repository) {
		this.repository = repository;
	}

	/**
	 * Da de alta un consultorio. La revisión previa da un error claro en el caso normal; si dos
	 * altas con el mismo slug compiten, el {@code UNIQUE} de la base es la barrera final.
	 */
	public Tenant crear(Slug slug, String nombre) {
		if (repository.existsBySlug(slug)) {
			throw new SlugDuplicadoException(slug);
		}
		return repository.save(Tenant.crear(slug, nombre));
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
		switch (destino) {
			case ACTIVO -> tenant.reactivar();
			case SUSPENDIDO -> tenant.suspender();
			case CANCELADO -> tenant.cancelar();
		}
		return tenant;
	}

	private Tenant buscar(UUID id) {
		return repository.findById(id).orElseThrow(() -> new TenantNoEncontradoException(id));
	}

}
