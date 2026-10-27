package dev.jorgemiguel.nutrikit.shared.tenancy;

import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Al iniciar cada transacción, fija en Postgres la variable {@code app.tenant_id} que usan las
 * políticas de Row-Level Security.
 *
 * <p>Se fija con alcance de transacción ({@code set_config(..., true)}): Postgres la borra en
 * el commit o el rollback. Así una conexión que vuelve al pool de HikariCP nunca conserva el
 * consultorio de la petición anterior, aunque algo falle a medio camino.
 *
 * <p>Sin consultorio en contexto no se fija nada y las tablas con RLS no devuelven filas.
 */
class JpaTransactionManagerConTenant extends JpaTransactionManager {

	static final String VARIABLE = "app.tenant_id";

	JpaTransactionManagerConTenant(EntityManagerFactory emf) {
		super(emf);
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		super.doBegin(transaction, definition);
		TenantContext.actual().ifPresent(this::fijarTenantEnLaBase);
	}

	private void fijarTenantEnLaBase(UUID tenantId) {
		var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
		holder.getEntityManager()
				.createNativeQuery("SELECT set_config('" + VARIABLE + "', :tenantId, true)")
				.setParameter("tenantId", tenantId.toString())
				.getSingleResult();
	}

}
