package dev.jorgemiguel.nutrikit.tenants.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;

/**
 * Prueba de persistencia contra Postgres real: Flyway crea el esquema y Hibernate lo valida
 * ({@code ddl-auto=validate}). Cada prueba corre en una transacción que se revierte al final.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TenantRepositoryTest {

	@Autowired
	private TenantRepository repository;

	@Autowired
	private TestEntityManager em;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void guardaYRecuperaUnTenantPorId() {
		Tenant guardado = repository.save(Tenant.crear(Slug.of("nutri-ana"), "Consultorio Ana"));
		em.flush();
		em.clear();

		Tenant leido = repository.findById(guardado.getId()).orElseThrow();

		assertThat(leido.getId()).isNotNull().isEqualTo(guardado.getId());
		assertThat(leido.getSlug()).isEqualTo(Slug.of("nutri-ana"));
		assertThat(leido.getNombre()).isEqualTo("Consultorio Ana");
		assertThat(leido.getEstado()).isEqualTo(EstadoTenant.ACTIVO);
		assertThat(leido.getCreadoEn()).isNotNull();
	}

	@Test
	void generaIdsUuidVersion7() {
		Tenant guardado = repository.save(Tenant.crear(Slug.of("nutri-ana"), "Consultorio Ana"));

		assertThat(guardado.getId().version()).isEqualTo(7);
	}

	@Test
	void buscaPorSlug() {
		repository.save(Tenant.crear(Slug.of("nutri-ana"), "Consultorio Ana"));
		em.flush();
		em.clear();

		assertThat(repository.findBySlug(Slug.of("nutri-ana"))).isPresent();
		assertThat(repository.findBySlug(Slug.of("no-existe"))).isEmpty();
		assertThat(repository.existsBySlug(Slug.of("nutri-ana"))).isTrue();
	}

	@Test
	void unSlugDuplicadoNoSeGuarda() {
		repository.save(Tenant.crear(Slug.of("nutri-ana"), "Consultorio Ana"));
		em.flush();

		repository.save(Tenant.crear(Slug.of("nutri-ana"), "Otro consultorio"));

		// TestEntityManager habla directo con Hibernate, así que la excepción no pasa por la
		// traducción de Spring (DataIntegrityViolationException) que sí aplica al repositorio.
		assertThatThrownBy(em::flush)
				.isInstanceOfSatisfying(ConstraintViolationException.class,
						e -> assertThat(e.getConstraintName()).isEqualTo("tenant_slug_key"));
	}

	@Test
	void persisteElCambioDeEstado() {
		Tenant tenant = repository.save(Tenant.crear(Slug.of("nutri-ana"), "Consultorio Ana"));
		tenant.suspender();
		em.flush();
		em.clear();

		String estadoEnBase = jdbc.sql("SELECT estado FROM tenant WHERE id = ?")
				.param(tenant.getId())
				.query(String.class)
				.single();

		assertThat(estadoEnBase).isEqualTo("SUSPENDIDO");
	}

}
