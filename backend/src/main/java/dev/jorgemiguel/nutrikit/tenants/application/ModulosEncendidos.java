package dev.jorgemiguel.nutrikit.tenants.application;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModulo;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModuloRepository;

/**
 * Qué módulos tiene encendidos cada consultorio. Se consulta en cada petición, así que se guarda
 * en caché (Caffeine, ver {@code spring.cache.caffeine.spec}).
 *
 * <ul>
 * <li>En esta instancia, un cambio se ve de inmediato: {@link #olvidar} se llama después del
 * commit de cada cambio.</li>
 * <li>En otras instancias, a más tardar al vencer la entrada (60 segundos).</li>
 * </ul>
 */
@Component
public class ModulosEncendidos {

	static final String CACHE = "modulos-encendidos";

	private final TenantModuloRepository repository;
	private final TransactionTemplate tx;

	ModulosEncendidos(TenantModuloRepository repository, TransactionTemplate tx) {
		this.repository = repository;
		this.tx = tx;
	}

	/**
	 * Códigos de los módulos encendidos. Entra al consultorio antes de abrir la transacción para
	 * que RLS deje leer sus filas, aunque se llame desde fuera de una petición.
	 */
	@Cacheable(CACHE)
	public Set<String> codigosEncendidos(UUID tenantId) {
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> repository.findAllByOrderByModuloAsc()
				.stream()
				.filter(TenantModulo::isHabilitado)
				.map(TenantModulo::getModulo)
				.collect(Collectors.toUnmodifiableSet())));
	}

	@CacheEvict(CACHE)
	public void olvidar(UUID tenantId) {
	}

}
