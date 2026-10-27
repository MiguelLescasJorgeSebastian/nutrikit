package dev.jorgemiguel.nutrikit.tenants.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModulo;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModuloRepository;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/** Casos de uso sobre el consultorio de la petición en curso. */
@Service
@Transactional(readOnly = true)
public class ConsultorioActualService {

	private final TenantRepository tenants;
	private final TenantModuloRepository modulos;

	ConsultorioActualService(TenantRepository tenants, TenantModuloRepository modulos) {
		this.tenants = tenants;
		this.modulos = modulos;
	}

	public Tenant obtener() {
		var id = TenantContext.requerido();
		return tenants.findById(id).orElseThrow(() -> new TenantNoEncontradoException(id));
	}

	public List<TenantModulo> modulos() {
		return modulos.findAllByOrderByModuloAsc();
	}

}
