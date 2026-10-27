package dev.jorgemiguel.nutrikit.tenants.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * Persistencia de consultorios. Extiende {@link Repository} y no {@code JpaRepository} para
 * exponer solo las operaciones que el dominio necesita (sin {@code deleteAll}, por ejemplo).
 */
public interface TenantRepository extends Repository<Tenant, UUID> {

	Tenant save(Tenant tenant);

	Optional<Tenant> findById(UUID id);

	Optional<Tenant> findBySlug(Slug slug);

	boolean existsBySlug(Slug slug);

}
