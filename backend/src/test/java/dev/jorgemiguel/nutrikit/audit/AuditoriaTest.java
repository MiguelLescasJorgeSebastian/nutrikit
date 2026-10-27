package dev.jorgemiguel.nutrikit.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.iam.InicioDeSesionExitoso;
import dev.jorgemiguel.nutrikit.iam.InicioDeSesionFallido;
import dev.jorgemiguel.nutrikit.shared.Origen;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.shared.TipoDeActor;
import dev.jorgemiguel.nutrikit.tenants.EstadoDeTenantCambiado;
import dev.jorgemiguel.nutrikit.tenants.TenantCreado;

/**
 * Bitácora de auditoría: cada evento produce su registro, la tabla es append-only para la app,
 * está aislada por consultorio y particionada por mes.
 *
 * <p>Los registros se escriben en otro hilo después del commit, así que las pruebas esperan a que
 * aparezcan ({@link Scenario} o Awaitility) en lugar de leerlos de inmediato.
 */
@SpringBootTest(properties = {
		"nutrikit.tenancy.cabecera-habilitada=true",
		"nutrikit.seguridad.operador.email=operador@nutrikit.test",
		// operador-dev
		"nutrikit.seguridad.operador.password-hash={bcrypt}$2a$10$qdeesc9rka5wcWdzdenUJ.GVQV0P3l0bwOfMhpa2wJtJp7ClWEOu2" })
@AutoConfigureMockMvc
@EnableScenarios
@Import(TestcontainersConfiguration.class)
class AuditoriaTest {

	private static final Origen DESDE_EL_NAVEGADOR =
			new Origen(TipoDeActor.USUARIO, UUID.randomUUID(), "203.0.113.7", "Mozilla/5.0 (prueba)");

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ApplicationEventPublisher eventos;

	/** Registros de un consultorio, leídos dentro de su contexto (RLS). */
	private List<Map<String, Object>> registrosDe(UUID tenantId) {
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> jdbc.sql("""
				SELECT accion, actor_tipo, actor_id, recurso_tipo, recurso_id, host(ip) AS ip, user_agent, ocurrido_en,
				       tableoid::regclass::text AS particion
				FROM auditoria ORDER BY id
				""").query().listOfRows()));
	}

	@Nested
	class CadaEventoDejaUnRegistro {

		@Test
		void tenantCreado(Scenario scenario) {
			UUID tenant = UUID.randomUUID();
			Instant cuando = Instant.now().truncatedTo(ChronoUnit.MICROS);

			scenario.publish(new TenantCreado(tenant, "audit-creado", DESDE_EL_NAVEGADOR, cuando))
					.andWaitForStateChange(() -> registrosDe(tenant))
					.andVerify(registros -> {
						assertThat(registros).singleElement().satisfies(r -> {
							assertThat(r).containsEntry("accion", "TENANT_CREADO")
									.containsEntry("recurso_tipo", "TENANT")
									.containsEntry("recurso_id", tenant)
									.containsEntry("actor_tipo", "USUARIO")
									.containsEntry("actor_id", DESDE_EL_NAVEGADOR.actorId())
									.containsEntry("ip", "203.0.113.7")
									.containsEntry("user_agent", "Mozilla/5.0 (prueba)");
							assertThat(((java.sql.Timestamp) r.get("ocurrido_en")).toInstant()).isEqualTo(cuando);
						});
					});
		}

		@Test
		void estadoDeTenantCambiado(Scenario scenario) {
			UUID tenant = UUID.randomUUID();

			scenario.publish(new EstadoDeTenantCambiado(tenant, "ACTIVO", "SUSPENDIDO", DESDE_EL_NAVEGADOR, Instant.now()))
					.andWaitForStateChange(() -> registrosDe(tenant))
					.andVerify(registros -> assertThat(registros).singleElement()
							.satisfies(r -> assertThat(r).containsEntry("accion", "TENANT_ESTADO_CAMBIADO")));
		}

		@Test
		void inicioDeSesionExitoso(Scenario scenario) {
			UUID tenant = UUID.randomUUID();
			UUID usuario = UUID.randomUUID();

			scenario.publish(new InicioDeSesionExitoso(tenant, usuario, DESDE_EL_NAVEGADOR, Instant.now()))
					.andWaitForStateChange(() -> registrosDe(tenant))
					.andVerify(registros -> assertThat(registros).singleElement().satisfies(r -> assertThat(r)
							.containsEntry("accion", "LOGIN_EXITOSO")
							.containsEntry("recurso_tipo", "USUARIO")
							.containsEntry("recurso_id", usuario)));
		}

		@Test
		void inicioDeSesionFallido(Scenario scenario) {
			UUID tenant = UUID.randomUUID();
			var anonimo = DESDE_EL_NAVEGADOR.conActor(TipoDeActor.ANONIMO, null);

			scenario.publish(new InicioDeSesionFallido(tenant, null, anonimo, Instant.now()))
					.andWaitForStateChange(() -> registrosDe(tenant))
					.andVerify(registros -> assertThat(registros).singleElement().satisfies(r -> assertThat(r)
							.containsEntry("accion", "LOGIN_FALLIDO")
							.containsEntry("actor_tipo", "ANONIMO")
							.containsEntry("actor_id", null)
							.containsEntry("recurso_id", null)));
		}

		@Test
		void unEventoCuyaTransaccionTerminaEnRollbackNoSeAudita() {
			UUID tenant = UUID.randomUUID();

			assertThatThrownBy(() -> tx.executeWithoutResult(estado -> {
				eventos.publishEvent(new TenantCreado(tenant, "audit-rollback", DESDE_EL_NAVEGADOR, Instant.now()));
				throw new IllegalStateException("el alta falló después de publicar el evento");
			})).isInstanceOf(IllegalStateException.class);

			await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2))
					.until(() -> registrosDe(tenant).isEmpty());
		}

	}

	@Nested
	class DePuntaAPunta {

		private String bearer(String token) {
			return "Bearer " + token;
		}

		private String json(org.springframework.test.web.servlet.ResultActions resultado, String ruta) throws Exception {
			return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta);
		}

		/** Las cuatro acciones reales por HTTP: el registro incluye actor, IP y navegador de la petición. */
		@Test
		void lasCuatroAccionesQuedanEnLaBitacoraConSuOrigen() throws Exception {
			String operador = json(mvc.perform(post("/api/operador/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\": \"operador@nutrikit.test\", \"password\": \"operador-dev\"}"))
					.andExpect(status().isOk()), "$.accessToken");

			UUID tenant = UUID.fromString(json(mvc.perform(post("/api/operador/tenants")
					.header(HttpHeaders.AUTHORIZATION, bearer(operador))
					.header(HttpHeaders.USER_AGENT, "PanelOperador/1.0")
					.with(r -> { r.setRemoteAddr("198.51.100.20"); return r; })
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"slug\": \"audit-e2e\", \"nombre\": \"Consultorio auditado\"}"))
					.andExpect(status().isCreated()), "$.id"));
			try {
				mvc.perform(post("/api/operador/tenants/%s/usuarios".formatted(tenant))
						.header(HttpHeaders.AUTHORIZATION, bearer(operador)).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ana@audit.mx", "nombre": "Ana", "rol": "TITULAR", "password": "contraseña-larga-ana"}
								"""))
						.andExpect(status().isCreated());

				var login = post("/api/auth/login").header("X-Tenant", "audit-e2e")
						.header(HttpHeaders.USER_AGENT, "Navegador/2.0")
						.with(r -> { r.setRemoteAddr("192.0.2.33"); return r; })
						.contentType(MediaType.APPLICATION_JSON);
				mvc.perform(login.content("{\"email\": \"ana@audit.mx\", \"password\": \"no-es-la-buena\"}"))
						.andExpect(status().isUnauthorized());
				mvc.perform(login.content("{\"email\": \"ana@audit.mx\", \"password\": \"contraseña-larga-ana\"}"))
						.andExpect(status().isOk());

				mvc.perform(patch("/api/operador/tenants/%s/estado".formatted(tenant))
						.header(HttpHeaders.AUTHORIZATION, bearer(operador)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"estado\": \"SUSPENDIDO\"}"))
						.andExpect(status().isOk());

				await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(registrosDe(tenant))
						.extracting(r -> r.get("accion"))
						.containsExactlyInAnyOrder("TENANT_CREADO", "LOGIN_FALLIDO", "LOGIN_EXITOSO",
								"TENANT_ESTADO_CAMBIADO"));

				var porAccion = registrosDe(tenant).stream()
						.collect(java.util.stream.Collectors.toMap(r -> (String) r.get("accion"), r -> r));
				assertThat(porAccion.get("TENANT_CREADO"))
						.containsEntry("actor_tipo", "OPERADOR")
						.containsEntry("ip", "198.51.100.20")
						.containsEntry("user_agent", "PanelOperador/1.0");
				assertThat(porAccion.get("LOGIN_FALLIDO"))
						.containsEntry("actor_tipo", "ANONIMO")
						.containsEntry("ip", "192.0.2.33")
						.containsEntry("user_agent", "Navegador/2.0");
				// La cuenta atacada queda registrada aunque la respuesta no lo revele.
				assertThat(porAccion.get("LOGIN_FALLIDO").get("recurso_id")).isNotNull();
				assertThat(porAccion.get("LOGIN_EXITOSO"))
						.containsEntry("actor_tipo", "USUARIO")
						.satisfies(r -> assertThat(r.get("actor_id")).isEqualTo(r.get("recurso_id")));
			}
			finally {
				jdbc.sql("DELETE FROM tenant WHERE id = ?").param(tenant).update();
			}
		}

	}

	@Nested
	class TablaAppendOnly {

		private UUID conUnRegistro(Scenario scenario) {
			UUID tenant = UUID.randomUUID();
			scenario.publish(new TenantCreado(tenant, "audit-inmutable", DESDE_EL_NAVEGADOR, Instant.now()))
					.andWaitForStateChange(() -> registrosDe(tenant))
					.andVerify(registros -> assertThat(registros).hasSize(1));
			return tenant;
		}

		private void ejecutarComo(UUID tenant, String sql) {
			TenantContext.ejecutarComo(tenant, () -> tx.executeWithoutResult(estado -> jdbc.sql(sql).update()));
		}

		@Test
		void laAppNoPuedeModificarRegistros(Scenario scenario) {
			UUID tenant = conUnRegistro(scenario);

			assertThatThrownBy(() -> ejecutarComo(tenant, "UPDATE auditoria SET accion = 'OTRA'"))
					.rootCause().hasMessageContaining("permission denied for table auditoria");
			assertThat(registrosDe(tenant)).singleElement()
					.satisfies(r -> assertThat(r).containsEntry("accion", "TENANT_CREADO"));
		}

		@Test
		void laAppNoPuedeBorrarRegistros(Scenario scenario) {
			UUID tenant = conUnRegistro(scenario);

			assertThatThrownBy(() -> ejecutarComo(tenant, "DELETE FROM auditoria"))
					.rootCause().hasMessageContaining("permission denied for table auditoria");
			assertThat(registrosDe(tenant)).hasSize(1);
		}

		@Test
		void laAppNoPuedeSaltarseLaTablaPadreYEscribirEnUnaParticion(Scenario scenario) {
			UUID tenant = conUnRegistro(scenario);
			String particion = (String) registrosDe(tenant).getFirst().get("particion");

			assertThatThrownBy(() -> ejecutarComo(tenant, "DELETE FROM " + particion))
					.rootCause().hasMessageContaining("permission denied");
		}

		@Test
		void unConsultorioNoVeLaBitacoraDeOtro(Scenario scenario) {
			UUID tenant = conUnRegistro(scenario);

			assertThat(registrosDe(UUID.randomUUID())).isEmpty();
			assertThat(registrosDe(tenant)).hasSize(1);
		}

	}

	@Nested
	class Particiones {

		private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy_MM");

		@Test
		void cadaRegistroVaALaParticionDeSuMes(Scenario scenario) {
			UUID tenant = UUID.randomUUID();
			Instant ahora = Instant.now();

			scenario.publish(new TenantCreado(tenant, "audit-particion", DESDE_EL_NAVEGADOR, ahora))
					.andWaitForStateChange(() -> registrosDe(tenant))
					.andVerify(registros -> assertThat(registros.getFirst().get("particion"))
							.isEqualTo("auditoria_" + MES.format(ahora.atZone(ZoneOffset.UTC))));
		}

		@Test
		void elMesActualYLosDosSiguientesYaTienenParticion() {
			LocalDate mes = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
			List<String> existentes = jdbc.sql("""
					SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
					WHERE i.inhparent = 'auditoria'::regclass
					""").query(String.class).list();

			assertThat(existentes).contains("auditoria_" + MES.format(mes), "auditoria_" + MES.format(mes.plusMonths(1)),
					"auditoria_" + MES.format(mes.plusMonths(2)));
		}

		@Test
		void laAppCreaParticionesFuturasSinTenerPermisosDeDdl() {
			String creada = jdbc.sql("SELECT asegurar_particion_auditoria(DATE '2031-07-15')").query(String.class).single();
			String otraVez = jdbc.sql("SELECT asegurar_particion_auditoria(DATE '2031-07-01')").query(String.class).single();

			assertThat(creada).isEqualTo("auditoria_2031_07").isEqualTo(otraVez);
			// Directo, sin la función, no puede: ni siquiera tiene permiso de crear objetos en el esquema.
			assertThatThrownBy(() -> jdbc.sql("CREATE TABLE intento PARTITION OF auditoria "
					+ "FOR VALUES FROM ('2032-01-01') TO ('2032-02-01')").update())
					.rootCause().hasMessageContaining("permission denied for schema public");
		}

	}

}
