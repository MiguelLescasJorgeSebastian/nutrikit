package dev.jorgemiguel.nutrikit.patientaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Enlace privado del paciente de punta a punta. Cada prueba usa su propia IP de origen, porque el
 * límite de intentos por IP se comparte entre las pruebas del contexto.
 */
@SpringBootTest(properties = "nutrikit.tenancy.cabecera-habilitada=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EnlaceDelPacienteTest {

	private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	private record Consultorio(UUID id, String slug) {
	}

	private Consultorio consultorio;
	private String ana;
	private String ip;

	@BeforeEach
	void preparar() throws Exception {
		ip = "10.%d.%d.1".formatted(SIGUIENTE_IP.get() / 250, SIGUIENTE_IP.getAndIncrement() % 250);
		consultorio = consultorioNuevo();
		ana = paciente(consultorio, "Ana");
		mvc.perform(staff(consultorio, post("/api/pacientes/%s/consultas".formatted(ana))).content("""
				{"fecha": "2026-09-01T16:00:00Z", "notas": "Nota clínica privada",
				 "mediciones": [{"indicador": "peso", "valor": 70}, {"indicador": "talla", "valor": 165}]}
				""")).andExpect(status().isCreated());
	}

	// --- utilidades ---------------------------------------------------------------------------

	private Consultorio consultorioNuevo() throws Exception {
		String slug = "enlace-" + UUID.randomUUID().toString().substring(0, 8);
		String respuesta = mvc.perform(post("/api/operador/tenants").contentType(MediaType.APPLICATION_JSON)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
				.content("{\"slug\": \"%s\", \"nombre\": \"Consultorio\"}".formatted(slug)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		var nuevo = new Consultorio(UUID.fromString(JsonPath.read(respuesta, "$.id")), slug);
		mvc.perform(staff(nuevo, post("/api/avisos-de-privacidad")).content("{\"version\": \"1\", \"contenido\": \"Aviso\"}"))
				.andExpect(status().isCreated());
		return nuevo;
	}

	private MockHttpServletRequestBuilder staff(Consultorio c, MockHttpServletRequestBuilder peticion) {
		return staff(c, peticion, "TITULAR");
	}

	private MockHttpServletRequestBuilder staff(Consultorio c, MockHttpServletRequestBuilder peticion, String rol) {
		return peticion.header("X-Tenant", c.slug()).contentType(MediaType.APPLICATION_JSON)
				.with(jwt().jwt(t -> t.claim("tenant_id", c.id().toString()).claim("rol", rol))
						.authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
	}

	private String paciente(Consultorio c, String nombre) throws Exception {
		String id = JsonPath.read(mvc.perform(staff(c, post("/api/pacientes")).content("""
				{"nombre": "%s", "apellidos": "Ruiz", "fechaNacimiento": "1990-01-01", "sexo": "FEMENINO"}
				""".formatted(nombre))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(staff(c, post("/api/pacientes/%s/consentimientos".formatted(id))).content("{\"medio\": \"PRESENCIAL\"}"))
				.andExpect(status().isCreated());
		return id;
	}

	private String generar(String pacienteId, String configuracion) throws Exception {
		return JsonPath.read(mvc.perform(staff(consultorio, post("/api/pacientes/%s/enlace".formatted(pacienteId)))
				.content(configuracion)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.token");
	}

	/** El paciente: sin JWT, solo con el enlace, en el subdominio de su consultorio. */
	private ResultActions abrir(String slug, String token, String pin) throws Exception {
		var peticion = get("/api/acceso/" + token).header("X-Tenant", slug)
				.header(HttpHeaders.USER_AGENT, "Navegador del paciente/1.0")
				.with(r -> {
					r.setRemoteAddr(ip);
					return r;
				});
		if (pin != null) {
			peticion.header("X-Pin", pin);
		}
		return mvc.perform(peticion);
	}

	private ResultActions abrir(String token) throws Exception {
		return abrir(consultorio.slug(), token, null);
	}

	private <T> T enConsultorio(Supplier<T> accion) {
		return TenantContext.ejecutarComo(consultorio.id(), () -> tx.execute(estado -> accion.get()));
	}

	// --- pruebas ------------------------------------------------------------------------------

	@Nested
	class Pagina {

		@Test
		void muestraDatosGeneralesUltimaConsultaYSerieDePesoSinNotas() throws Exception {
			String token = generar(ana, "{}");

			abrir(token).andExpect(status().isOk())
					.andExpect(jsonPath("$.nombre").value("Ana"))
					.andExpect(jsonPath("$.ultimaConsulta.fecha").value("2026-09-01T16:00:00Z"))
					.andExpect(jsonPath("$.ultimaConsulta.mediciones[?(@.indicador == 'peso')].valor").value(70))
					.andExpect(jsonPath("$.ultimaConsulta.mediciones[?(@.indicador == 'imc')].valor").value(25.71))
					.andExpect(jsonPath("$.peso.unidad").value("kg"))
					.andExpect(jsonPath("$.peso.puntos", hasSize(1)))
					.andExpect(jsonPath("$.ultimaConsulta.notas").doesNotExist())
					.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
							.string(org.hamcrest.Matchers.not(containsString("Nota clínica privada"))));
		}

		@Test
		void incluyeLasCabecerasDePrivacidad() throws Exception {
			abrir(generar(ana, "{}")).andExpect(status().isOk())
					.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
					.andExpect(header().string("Referrer-Policy", "no-referrer"))
					.andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
			// También en los errores.
			abrir("token-inexistente").andExpect(status().isNotFound())
					.andExpect(header().string("Referrer-Policy", "no-referrer"));
		}

	}

	@Nested
	class Credencial {

		@Test
		void enLaBaseSoloExisteElHashDelToken() throws Exception {
			String token = generar(ana, "{}");

			Map<String, Object> fila = enConsultorio(() -> jdbc.sql("SELECT * FROM enlace_acceso WHERE paciente_id = ?")
					.param(UUID.fromString(ana)).query().singleRow());

			assertThat(token).hasSize(43); // 32 bytes en Base64 URL-safe sin relleno
			assertThat(fila.get("token_hash")).isEqualTo(sha256(token)).isNotEqualTo(token);
			assertThat(fila.values()).allSatisfy(valor -> assertThat(String.valueOf(valor)).doesNotContain(token));
		}

		@Test
		void elTokenSoloSeMuestraAlGenerarlo() throws Exception {
			String token = generar(ana, "{}");

			mvc.perform(staff(consultorio, get("/api/pacientes/%s/enlace".formatted(ana))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.vigente").value(true))
					.andExpect(jsonPath("$.token").doesNotExist())
					.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
							.string(org.hamcrest.Matchers.not(containsString(token))));
		}

		@Test
		void elTokenDeUnPacienteNuncaDevuelveDatosDeOtroNiDeOtroConsultorio() throws Exception {
			String beto = paciente(consultorio, "Beto");
			String deAna = generar(ana, "{}");
			String deBeto = generar(beto, "{}");

			abrir(deAna).andExpect(jsonPath("$.nombre").value("Ana"));
			abrir(deBeto).andExpect(jsonPath("$.nombre").value("Beto")).andExpect(jsonPath("$.ultimaConsulta").doesNotExist());

			// El token de Ana en el subdominio de otro consultorio no existe.
			Consultorio otro = consultorioNuevo();
			abrir(otro.slug(), deAna, null).andExpect(status().isNotFound());
		}

		@Test
		void revocarORegenerarInvalidanElEnlaceAnteriorAlInstante() throws Exception {
			String primero = generar(ana, "{}");
			abrir(primero).andExpect(status().isOk());

			String segundo = generar(ana, "{}");
			abrir(primero).andExpect(status().isNotFound());
			abrir(segundo).andExpect(status().isOk());

			mvc.perform(staff(consultorio, delete("/api/pacientes/%s/enlace".formatted(ana)))).andExpect(status().isNoContent());
			abrir(segundo).andExpect(status().isNotFound());

			Integer vigentes = enConsultorio(() -> jdbc.sql(
					"SELECT count(*) FROM enlace_acceso WHERE paciente_id = ? AND revocado_en IS NULL")
					.param(UUID.fromString(ana)).query(Integer.class).single());
			assertThat(vigentes).isZero();
		}

		@Test
		void unEnlaceCaducadoResponde410() throws Exception {
			String token = generar(ana, "{\"expiraEn\": \"2099-01-01T00:00:00Z\"}");
			abrir(token).andExpect(status().isOk());

			enConsultorio(() -> jdbc.sql("UPDATE enlace_acceso SET expira_en = now() - interval '1 minute' WHERE paciente_id = ?")
					.param(UUID.fromString(ana)).update());

			abrir(token).andExpect(status().isGone()).andExpect(jsonPath("$.title").value("Enlace caducado"));
		}

		@Test
		void probarTokensAlAzarDesdeUnaIpTerminaEn429() throws Exception {
			for (int i = 0; i < 20; i++) {
				abrir("token-falso-" + i).andExpect(status().isNotFound());
			}

			abrir(generar(ana, "{}")).andExpect(status().isTooManyRequests())
					.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
		}

	}

	@Nested
	class Pin {

		@Test
		void conPinActivoSinPinOConPinIncorrectoResponde401() throws Exception {
			String token = generar(ana, "{\"pin\": \"4821\"}");

			abrir(consultorio.slug(), token, null).andExpect(status().isUnauthorized());
			abrir(consultorio.slug(), token, "0000").andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.title").value("PIN requerido"));
			abrir(consultorio.slug(), token, "4821").andExpect(status().isOk());
		}

		@Test
		void trasVariosPinIncorrectosResponde429AunqueElPinSeaCorrecto() throws Exception {
			String token = generar(ana, "{\"pin\": \"4821\"}");
			for (int i = 0; i < 5; i++) {
				abrir(consultorio.slug(), token, "000" + i).andExpect(status().isUnauthorized());
			}

			abrir(consultorio.slug(), token, "4821").andExpect(status().isTooManyRequests());
		}

		@Test
		void elPinSeGuardaConArgon2() throws Exception {
			generar(ana, "{\"pin\": \"4821\"}");

			String pinHash = enConsultorio(() -> jdbc.sql("SELECT pin_hash FROM enlace_acceso WHERE paciente_id = ?")
					.param(UUID.fromString(ana)).query(String.class).single());

			assertThat(pinHash).startsWith("$argon2id$").doesNotContain("4821");
		}

		@Test
		void configurarPinYCaducidadNoCambiaElToken() throws Exception {
			String token = generar(ana, "{}");

			mvc.perform(staff(consultorio, put("/api/pacientes/%s/enlace".formatted(ana))).content("{\"pin\": \"1234\"}"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.conPin").value(true));
			abrir(consultorio.slug(), token, "1234").andExpect(status().isOk());

			mvc.perform(staff(consultorio, put("/api/pacientes/%s/enlace".formatted(ana))).content("{\"pin\": \"12\"}"))
					.andExpect(status().isBadRequest());
			mvc.perform(staff(consultorio, put("/api/pacientes/%s/enlace".formatted(ana)))
					.content("{\"expiraEn\": \"2020-01-01T00:00:00Z\"}")).andExpect(status().isBadRequest());
		}

	}

	@Nested
	class Bitacora {

		@Test
		void cadaAccesoQuedaConIpYUserAgent() throws Exception {
			String token = generar(ana, "{\"pin\": \"4821\"}");
			abrir(consultorio.slug(), token, "0000").andExpect(status().isUnauthorized());
			abrir(consultorio.slug(), token, "4821").andExpect(status().isOk());

			await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(registros())
					.extracting(r -> r.get("accion"))
					.contains("ENLACE_GENERADO", "ACCESO_PACIENTE_RECHAZADO", "ACCESO_PACIENTE"));
			assertThat(registros()).filteredOn(r -> "ACCESO_PACIENTE".equals(r.get("accion"))).singleElement()
					.satisfies(r -> assertThat(r)
							.containsEntry("actor_tipo", "PACIENTE")
							.containsEntry("actor_id", UUID.fromString(ana))
							.containsEntry("ip", ip)
							.containsEntry("user_agent", "Navegador del paciente/1.0"));
		}

		private List<Map<String, Object>> registros() {
			return enConsultorio(() -> jdbc.sql("""
					SELECT accion, actor_tipo, actor_id, host(ip) AS ip, user_agent FROM auditoria
					WHERE recurso_tipo = 'ENLACE_ACCESO' AND recurso_id = ? ORDER BY id
					""").param(UUID.fromString(ana)).query().listOfRows());
		}

	}

	@Nested
	class Permisos {

		@Test
		void unaAsistenteNoGeneraEnlaces() throws Exception {
			mvc.perform(staff(consultorio, post("/api/pacientes/%s/enlace".formatted(ana)), "ASISTENTE").content("{}"))
					.andExpect(status().isForbidden());
		}

		@Test
		void conElModuloApagadoNiElPacienteNiElPanelLlegan() throws Exception {
			String token = generar(ana, "{}");
			mvc.perform(put("/api/operador/tenants/%s/modulos/patientaccess".formatted(consultorio.id()))
					.contentType(MediaType.APPLICATION_JSON).content("{\"habilitado\": false}")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))).andExpect(status().isOk());

			abrir(token).andExpect(status().isNotFound());
			mvc.perform(staff(consultorio, get("/api/pacientes/%s/enlace".formatted(ana)))).andExpect(status().isNotFound());
		}

		@Test
		void unPacienteDadoDeBajaYaNoEntra() throws Exception {
			String token = generar(ana, "{}");

			mvc.perform(staff(consultorio, delete("/api/pacientes/" + ana))).andExpect(status().isNoContent());

			abrir(token).andExpect(status().isNotFound());
		}

	}

	private static String sha256(String texto) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
	}

}
