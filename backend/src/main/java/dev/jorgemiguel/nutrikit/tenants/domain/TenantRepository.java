package dev.jorgemiguel.nutrikit.tenants.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Persistencia de consultorios. Extiende {@link Repository} y no {@code JpaRepository} para
 * exponer solo las operaciones que el dominio necesita (sin {@code deleteAll}, por ejemplo).
 */
public interface TenantRepository extends Repository<Tenant, UUID> {

	Tenant save(Tenant tenant);

	Optional<Tenant> findById(UUID id);

	@Query("SELECT t.id FROM Tenant t ORDER BY t.id")
	List<UUID> findAllIds();

	Page<Tenant> findAll(Pageable pageable);

	Optional<Tenant> findBySlug(Slug slug);

	boolean existsBySlug(Slug slug);

}
