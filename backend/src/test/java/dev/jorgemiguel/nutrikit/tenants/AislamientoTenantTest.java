package dev.jorgemiguel.nutrikit.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.domain.Slug;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModulo;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModuloRepository;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/**
 * Aislamiento entre consultorios con las dos barreras: el filtro de Hibernate (aplicación) y
 * Row-Level Security (base). Las pruebas se conectan como el usuario de la app, igual que en
 * producción.
 *
 * <p>Pool de una sola conexión: así cada transacción reutiliza la misma conexión física y se
 * puede comprobar que el consultorio no se queda pegado a ella.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
@Import(TestcontainersConfiguration.class)
class AislamientoTenantTest {

	@Autowired
	private TenantRepository tenants;

	@Autowired
	private TenantModuloRepository modulos;

	@Autowired
	private TransactionTemplate tx;

	@Autowired
	private JdbcClient jdbc;

	private UUID ana;
	private UUID beto;

	@BeforeEach
	void dosConsultoriosConSusModulos() {
		ana = tenants.save(Tenant.crear(Slug.of("aislamiento-ana"), "Consultorio Ana")).getId();
		beto = tenants.save(Tenant.crear(Slug.of("aislamiento-beto"), "Consultorio Beto")).getId();

		registrarModulos(ana, ModuloDelConsultorio.AGENDA, ModuloDelConsultorio.PACIENTES);
		registrarModulos(beto, ModuloDelConsultorio.AGENDA, ModuloDelConsultorio.SITIO_WEB,
				ModuloDelConsultorio.PLANES_DE_ALIMENTACION);
	}

	@AfterEach
	void borrarConsultorios() {
		// tenant no tiene RLS; el ON DELETE CASCADE borra sus módulos.
		jdbc.sql("DELETE FROM tenant WHERE id IN (?, ?)").params(ana, beto).update();
	}

	private void registrarModulos(UUID tenantId, ModuloDelConsultorio... nombres) {
		TenantContext.ejecutarComo(tenantId, () -> tx.executeWithoutResult(estado -> {
			for (ModuloDelConsultorio nombre : nombres) {
				modulos.save(TenantModulo.registrar(nombre, true));
			}
		}));
	}

	private <T> T comoConsultorio(UUID tenantId, java.util.function.Supplier<T> consulta) {
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> consulta.get()));
	}

	@Test
	void cadaConsultorioVeSoloSusModulos() {
		List<TenantModulo> deAna = comoConsultorio(ana, modulos::findAllByOrderByModuloAsc);
		List<TenantModulo> deBeto = comoConsultorio(beto, modulos::findAllByOrderByModuloAsc);

		assertThat(deAna).extracting(TenantModulo::getModulo).containsExactly("patients", "scheduling");
		assertThat(deAna).allSatisfy(m -> assertThat(m.getTenantId()).isEqualTo(ana));
		assertThat(deBeto).extracting(TenantModulo::getModulo).containsExactly("mealplans", "scheduling", "website");
	}

	@Test
	void findByIdNoDevuelveElRegistroDeOtroConsultorio() {
		var llaveDeBeto = new TenantModulo.Llave(beto, "mealplans");

		assertThat(comoConsultorio(ana, () -> modulos.findById(llaveDeBeto))).isEmpty();
		assertThat(comoConsultorio(beto, () -> modulos.findById(llaveDeBeto))).isPresent();
	}

	/**
	 * Barrera 2 sola: SQL nativo sin {@code WHERE}, que el filtro de Hibernate no toca. Solo RLS
	 * puede impedir que Ana vea los módulos de Beto.
	 */
	@Test
	void rlsAislaAunqueLaConsultaNoFiltrePorTenant() {
		List<UUID> visibles = comoConsultorio(ana,
				() -> jdbc.sql("SELECT tenant_id FROM tenant_modulo").query(UUID.class).list());

		assertThat(visibles).hasSize(2).containsOnly(ana);
	}

	/**
	 * Barrera 1 sola: la variable de RLS apunta a Beto, pero el contexto de la app es Ana. RLS
	 * deja pasar las filas de Beto (lo confirma el SQL nativo) y aun así el repositorio no
	 * devuelve nada, porque el filtro de Hibernate exige las de Ana.
	 */
	@Test
	void elFiltroDeLaAplicacionAislaAunqueRlsDejePasar() {
		Map<String, Integer> resultado = TenantContext.ejecutarComo(ana, () -> tx.execute(estado -> {
			jdbc.sql("SELECT set_config('app.tenant_id', ?, true)").param(beto.toString()).query(String.class).single();
			int filasQueDejaPasarRls = jdbc.sql("SELECT count(*) FROM tenant_modulo").query(Integer.class).single();
			int filasDelRepositorio = modulos.findAllByOrderByModuloAsc().size();
			return Map.of("rls", filasQueDejaPasarRls, "repositorio", filasDelRepositorio);
		}));

		assertThat(resultado).containsEntry("rls", 3).containsEntry("repositorio", 0);
	}

	@Test
	void sinConsultorioNoSeVeNingunDato() {
		Integer filas = tx.execute(estado -> jdbc.sql("SELECT count(*) FROM tenant_modulo").query(Integer.class).single());

		List<TenantModulo> delRepositorio = tx.execute(estado -> modulos.findAllByOrderByModuloAsc());

		assertThat(filas).isZero();
		assertThat(delRepositorio).isEmpty();
	}

	@Test
	void rlsImpideEscribirDatosDeOtroConsultorio() {
		assertThatThrownBy(() -> TenantContext.ejecutarComo(ana, () -> tx.executeWithoutResult(estado ->
				jdbc.sql("INSERT INTO tenant_modulo (tenant_id, modulo) VALUES (?, 'integrations')").param(beto).update())))
				.rootCause().hasMessageContaining("violates row-level security policy");
	}

	@Test
	void elConsultorioNoSeQuedaEnLaConexionAlTerminarLaTransaccion() {
		comoConsultorio(ana, modulos::findAllByOrderByModuloAsc);

		// Misma conexión física (pool de 1), nueva transacción sin consultorio.
		String variable = tx.execute(estado ->
				jdbc.sql("SELECT current_setting('app.tenant_id', true)").query(String.class).optional().orElse(null));
		Integer filas = tx.execute(estado -> jdbc.sql("SELECT count(*) FROM tenant_modulo").query(Integer.class).single());

		assertThat(variable).isNullOrEmpty();
		assertThat(filas).isZero();
	}

	@Test
	void laAppSeConectaConUnUsuarioQueNoEsDuenoNiPuedeSaltarseRls() {
		Map<String, Object> usuario = jdbc.sql("""
				SELECT current_user AS usuario, r.rolsuper AS superusuario, r.rolbypassrls AS salta_rls,
				       (SELECT tableowner FROM pg_tables WHERE tablename = 'tenant_modulo') AS dueno
				FROM pg_roles r WHERE r.rolname = current_user
				""").query().singleRow();

		assertThat(usuario.get("usuario")).isEqualTo(TestcontainersConfiguration.USUARIO_APP);
		assertThat(usuario.get("superusuario")).isEqualTo(false);
		assertThat(usuario.get("salta_rls")).isEqualTo(false);
		assertThat(usuario.get("dueno")).isNotEqualTo(TestcontainersConfiguration.USUARIO_APP);
	}

	@Test
	void laAppNoPuedeCambiarElEsquema() {
		assertThatThrownBy(() -> jdbc.sql("ALTER TABLE tenant_modulo DISABLE ROW LEVEL SECURITY").update())
				.rootCause().hasMessageContaining("must be owner");
	}

}
