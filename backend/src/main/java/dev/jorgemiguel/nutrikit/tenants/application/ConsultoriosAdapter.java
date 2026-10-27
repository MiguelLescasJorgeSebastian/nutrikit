package dev.jorgemiguel.nutrikit.tenants.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.tenants.Consultorios;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

@Component
@Transactional(readOnly = true)
class ConsultoriosAdapter implements Consultorios {

	private final TenantRepository repository;

	ConsultoriosAdapter(TenantRepository repository) {
		this.repository = repository;
	}

	@Override
	public boolean existe(UUID tenantId) {
		return repository.findById(tenantId).isPresent();
	}

}
