package dev.jorgemiguel.nutrikit.tenants.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Módulos del consultorio actual. No recibe {@code tenantId}: el filtro de tenant y RLS ya
 * limitan cada consulta al consultorio de la petición.
 */
public interface TenantModuloRepository extends Repository<TenantModulo, TenantModulo.Llave> {

	TenantModulo save(TenantModulo modulo);

	List<TenantModulo> findAllByOrderByModuloAsc();

	Optional<TenantModulo> findById(TenantModulo.Llave llave);

}
