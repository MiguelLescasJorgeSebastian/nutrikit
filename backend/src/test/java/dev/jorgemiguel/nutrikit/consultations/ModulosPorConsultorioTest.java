package dev.jorgemiguel.nutrikit.consultations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.ModuloDelConsultorio;

/**
 * Módulos encendidos y apagados por consultorio, de punta a punta.
 *
 * <p>Esta clase vive en el paquete {@code consultations} a propósito: su controlador de prueba
 * pertenece así al módulo de consultas, igual que lo hará el controlador real. Spring no lo
 * registra en otras pruebas porque es una clase anidada de una prueba; aquí se importa a mano.
 */
@SpringBootTest(properties = "nutrikit.tenancy.cabecera-habilitada=true")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ModulosPorConsultorioTest.ConsultasDePrueba.class })
class ModulosPorConsultorioTest {

	private static final String RUTA_DE_CONSULTAS = "/api/consultas-de-prueba";

	@RestController
	static class ConsultasDePrueba {

		@GetMapping(RUTA_DE_CONSULTAS)
		Map<String, String> listar() {
			return Map.of("modulo", "consultations");
		}

	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	private final List<UUID> creados = new ArrayList<>();

	@AfterEach
	void borrar() {
		creados.forEach(id -> jdbc.sql("DELETE FROM tenant WHERE id = ?").param(id).update());
	}

	private static RequestPostProcessor operador() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"));
	}

	private static RequestPostProcessor usuarioDe(UUID tenantId) {
		return jwt().jwt(token -> token.claim("tenant_id", tenantId.toString()).claim("rol", "TITULAR"))
				.authorities(new SimpleGrantedAuthority("ROLE_TITULAR"));
	}

	private UUID crearConsultorio(String slug) throws Exception {
		String respuesta = mvc.perform(post("/api/operador/tenants").with(operador())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slug\": \"%s\", \"nombre\": \"Consultorio %s\"}".formatted(slug, slug)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(respuesta, "$.id"));
		creados.add(id);
		return id;
	}

	private ResultActions cambiar(UUID tenantId, String modulo, boolean habilitado) throws Exception {
		return mvc.perform(put("/api/operador/tenants/%s/modulos/%s".formatted(tenantId, modulo)).with(operador())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"habilitado\": %s}".formatted(habilitado)));
	}

	private ResultActions consultas(String slug, UUID tenantId) throws Exception {
		return mvc.perform(get(RUTA_DE_CONSULTAS).header("X-Tenant", slug).with(usuarioDe(tenantId)));
	}

	private List<Map<String, Object>> bitacora(UUID tenantId) {
		return TenantContext.ejecutarComo(tenantId, () -> tx.execute(estado -> jdbc.sql(
				"SELECT accion, actor_tipo, recurso_tipo FROM auditoria WHERE accion LIKE 'MODULO_%' ORDER BY id")
				.query().listOfRows()));
	}

	@Test
	void elControladorDePruebaPerteneceAlModuloDeConsultas() {
		assertThat(ModuloDelConsultorio.deClase(ConsultasDePrueba.class)).contains(ModuloDelConsultorio.CONSULTAS);
		assertThat(ModuloDelConsultorio.deClase(ModuloDelConsultorio.class)).isEmpty();
		assertThat(ModuloDelConsultorio.deClase(String.class)).isEmpty();
	}

	@Test
	void unConsultorioNuevoTieneTodosLosModulosConSuValorPorDefecto() throws Exception {
		UUID id = crearConsultorio("modulos-nuevo");

		var lista = mvc.perform(get("/api/operador/tenants/%s/modulos".formatted(id)).with(operador()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(ModuloDelConsultorio.values().length)));

		for (ModuloDelConsultorio modulo : ModuloDelConsultorio.values()) {
			String filtro = "$[?(@.modulo == '%s')]".formatted(modulo.codigo());
			lista.andExpect(jsonPath(filtro + ".habilitado").value(modulo.encendidoPorDefecto()))
					.andExpect(jsonPath(filtro + ".nucleo").value(modulo.nucleo()));
		}
	}

	@Test
	void elCatalogoDeJavaCoincideConElCheckDeLaBase() {
		List<String> enLaBase = jdbc.sql("""
				SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'tenant_modulo_modulo_check'
				""").query(String.class).list();

		assertThat(enLaBase).singleElement().satisfies(definicion -> {
			for (ModuloDelConsultorio modulo : ModuloDelConsultorio.values()) {
				assertThat(definicion).contains("'" + modulo.codigo() + "'");
			}
			// Ni más ni menos: cada código es un literal entre comillas simples.
			assertThat(definicion.chars().filter(c -> c == '\'').count()).isEqualTo(2L * ModuloDelConsultorio.values().length);
		});
	}

	@Test
	void unModuloDeNucleoNoSePuedeApagar() throws Exception {
		UUID id = crearConsultorio("modulos-nucleo");

		cambiar(id, "patients", false)
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.modulo").value("patients"));
		cambiar(id, "patients", true).andExpect(status().isOk());
	}

	@Test
	void unModuloInexistenteDevuelve404() throws Exception {
		UUID id = crearConsultorio("modulos-inexistente");

		cambiar(id, "contabilidad", true).andExpect(status().isNotFound());
		cambiar(UUID.randomUUID(), "scheduling", true).andExpect(status().isNotFound());
	}

	/**
	 * El criterio central: el mismo módulo encendido en un consultorio y apagado en otro. Primero se
	 * consulta con el módulo encendido para que el estado quede en caché; así la prueba demuestra
	 * que el cambio invalida la caché y se ve sin reiniciar.
	 */
	@Test
	void elMismoModuloEncendidoEnUnConsultorioYApagadoEnOtro() throws Exception {
		UUID ana = crearConsultorio("modulos-ana");
		UUID beto = crearConsultorio("modulos-beto");
		consultas("modulos-ana", ana).andExpect(status().isOk());
		consultas("modulos-beto", beto).andExpect(status().isOk());

		cambiar(beto, "consultations", false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.habilitado").value(false));

		consultas("modulos-ana", ana)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modulo").value("consultations"));
		consultas("modulos-beto", beto)
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		cambiar(beto, "consultations", true).andExpect(status().isOk());

		consultas("modulos-beto", beto).andExpect(status().isOk());
	}

	@Test
	void unModuloApagadoRespondeIgualQueUnaRutaQueNoExiste() throws Exception {
		UUID id = crearConsultorio("modulos-igual");
		cambiar(id, "consultations", false).andExpect(status().isOk());

		String apagado = consultas("modulos-igual", id).andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();
		String inexistente = mvc.perform(get("/api/ruta-que-no-existe").header("X-Tenant", "modulos-igual")
				.with(usuarioDe(id)))
				.andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();

		for (String campo : List.of("$.title", "$.status")) {
			assertThat((Object) JsonPath.read(apagado, campo)).isEqualTo(JsonPath.read(inexistente, campo));
		}
	}

	@Test
	void encenderYApagarQuedaEnLaBitacoraUnaSolaVezPorCambio() throws Exception {
		UUID id = crearConsultorio("modulos-auditoria");

		cambiar(id, "scheduling", true).andExpect(status().isOk());
		cambiar(id, "scheduling", true).andExpect(status().isOk()); // ya estaba encendido: no es un cambio
		cambiar(id, "scheduling", false).andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(bitacora(id))
				.extracting(r -> r.get("accion"))
				.containsExactly("MODULO_ENCENDIDO", "MODULO_APAGADO"));
		assertThat(bitacora(id)).allSatisfy(r -> assertThat(r)
				.containsEntry("actor_tipo", "OPERADOR")
				.containsEntry("recurso_tipo", "MODULO_SCHEDULING"));
	}

	@Test
	void losModulosSeVenDesdeElConsultorio() throws Exception {
		UUID id = crearConsultorio("modulos-vista");
		cambiar(id, "website", true).andExpect(status().isOk());

		mvc.perform(get("/api/consultorio/modulos").header("X-Tenant", "modulos-vista").with(usuarioDe(id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.modulo == 'website')].habilitado").value(true))
				.andExpect(jsonPath("$[?(@.modulo == 'patients')].nucleo").value(true));
		assertThat(Arrays.stream(ModuloDelConsultorio.values()).filter(ModuloDelConsultorio::nucleo))
				.containsExactly(ModuloDelConsultorio.PACIENTES);
	}

}
