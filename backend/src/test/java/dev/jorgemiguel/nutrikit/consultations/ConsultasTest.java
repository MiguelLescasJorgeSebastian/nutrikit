package dev.jorgemiguel.nutrikit.consultations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/** Consultas, mediciones e indicadores de punta a punta. */
@SpringBootTest(properties = "nutrikit.tenancy.cabecera-habilitada=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsultasTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	private Consultorio ana;

	private record Consultorio(UUID id, String slug) {
	}

	@BeforeEach
	void preparar() throws Exception {
		ana = consultorioNuevo();
	}

	// --- utilidades ---------------------------------------------------------------------------

	/** Por la API del operador, para que el consultorio tenga sus módulos registrados (#19). */
	private Consultorio consultorioNuevo() throws Exception {
		String slug = "consultas-" + UUID.randomUUID().toString().substring(0, 8);
		String respuesta = mvc.perform(post("/api/operador/tenants").contentType(MediaType.APPLICATION_JSON)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
				.content("{\"slug\": \"%s\", \"nombre\": \"Consultorio\"}".formatted(slug)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		var consultorio = new Consultorio(UUID.fromString(JsonPath.read(respuesta, "$.id")), slug);
		mvc.perform(como(consultorio, post("/api/avisos-de-privacidad"), "TITULAR")
				.content("{\"version\": \"1\", \"contenido\": \"Aviso\"}")).andExpect(status().isCreated());
		return consultorio;
	}

	private MockHttpServletRequestBuilder como(Consultorio consultorio, MockHttpServletRequestBuilder peticion, String rol) {
		return peticion.header("X-Tenant", consultorio.slug()).contentType(MediaType.APPLICATION_JSON)
				.with(jwt().jwt(t -> t.claim("tenant_id", consultorio.id().toString()).claim("rol", rol))
						.authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
	}

	private MockHttpServletRequestBuilder titular(MockHttpServletRequestBuilder peticion) {
		return como(ana, peticion, "TITULAR");
	}

	private String paciente(Consultorio consultorio, boolean conConsentimiento) throws Exception {
		String respuesta = mvc.perform(como(consultorio, post("/api/pacientes"), "TITULAR").content("""
				{"nombre": "Ana", "apellidos": "Ruiz", "fechaNacimiento": "1990-01-01", "sexo": "FEMENINO"}
				""")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(respuesta, "$.id");
		if (conConsentimiento) {
			mvc.perform(como(consultorio, post("/api/pacientes/%s/consentimientos".formatted(id)), "TITULAR")
					.content("{\"medio\": \"PRESENCIAL\"}")).andExpect(status().isCreated());
		}
		return id;
	}

	private ResultActions consulta(Consultorio consultorio, String pacienteId, String json) throws Exception {
		return mvc.perform(como(consultorio, post("/api/pacientes/%s/consultas".formatted(pacienteId)), "TITULAR")
				.content(json));
	}

	private static String conMediciones(String fecha, String mediciones) {
		return """
				{"fecha": %s, "motivo": "Seguimiento", "notas": "Buena adherencia al plan", "mediciones": [%s]}
				""".formatted(fecha == null ? "null" : "\"" + fecha + "\"", mediciones);
	}

	private <T> T enConsultorio(Consultorio consultorio, Supplier<T> accion) {
		return TenantContext.ejecutarComo(consultorio.id(), () -> tx.execute(estado -> accion.get()));
	}

	// --- pruebas ------------------------------------------------------------------------------

	@Nested
	class Indicadores {

		@Test
		void elCatalogoTraeLosIndicadoresGlobalesBase() throws Exception {
			mvc.perform(titular(get("/api/indicadores"))).andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.global == true)].codigo").value(org.hamcrest.Matchers.containsInAnyOrder(
							"peso", "talla", "circunferencia_cintura", "circunferencia_cadera", "porcentaje_grasa",
							"porcentaje_musculo", "grasa_visceral", "agua_corporal")));
		}

		@Test
		void unIndicadorNuevoSeUsaSinMigracionYSoloLoVeSuConsultorio() throws Exception {
			mvc.perform(titular(post("/api/indicadores")).content("""
					{"codigo": "perimetro_brazo", "nombre": "Perímetro de brazo", "unidad": "cm",
					 "valorMinimo": 10, "valorMaximo": 80}
					""")).andExpect(status().isCreated()).andExpect(jsonPath("$.global").value(false));

			// Se usa de inmediato en una consulta: es una fila, no una columna.
			String id = paciente(ana, true);
			consulta(ana, id, conMediciones(null, "{\"indicador\": \"perimetro_brazo\", \"valor\": 31.5}"))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.mediciones[0].nombre").value("Perímetro de brazo"));

			// Otro consultorio no lo ve ni lo puede usar.
			Consultorio beto = consultorioNuevo();
			mvc.perform(como(beto, get("/api/indicadores"), "TITULAR"))
					.andExpect(jsonPath("$[*].codigo", not(hasItem("perimetro_brazo"))));
			consulta(beto, paciente(beto, true), conMediciones(null, "{\"indicador\": \"perimetro_brazo\", \"valor\": 31}"))
					.andExpect(status().isBadRequest());
		}

		@Test
		void unCodigoNoPuedeRepetirUnoGlobalNiUnoPropio() throws Exception {
			String perimetro = """
					{"codigo": "perimetro_brazo", "nombre": "Brazo", "unidad": "cm", "valorMinimo": 10, "valorMaximo": 80}
					""";
			mvc.perform(titular(post("/api/indicadores")).content(perimetro)).andExpect(status().isCreated());

			mvc.perform(titular(post("/api/indicadores")).content(perimetro)).andExpect(status().isConflict());
			mvc.perform(titular(post("/api/indicadores")).content("""
					{"codigo": "peso", "nombre": "Mi peso", "unidad": "kg", "valorMinimo": 1, "valorMaximo": 300}
					""")).andExpect(status().isConflict());
		}

		@Test
		void unConsultorioNoPuedeCrearIndicadoresGlobales() {
			assertThatThrownBy(() -> enConsultorio(ana, () -> jdbc.sql("""
					INSERT INTO indicador (id, tenant_id, codigo, nombre, unidad, valor_minimo, valor_maximo)
					VALUES (gen_random_uuid(), NULL, 'intruso', 'Intruso', 'x', 0, 1)
					""").update())).rootCause().hasMessageContaining("row-level security");
		}

	}

	@Nested
	class RegistroDeConsultas {

		@Test
		void registraLaConsultaConSusMedicionesYNotasCifradas() throws Exception {
			String id = paciente(ana, true);

			var alta = consulta(ana, id, conMediciones(null, """
					{"indicador": "peso", "valor": 72.40}, {"indicador": "talla", "valor": 165},
					{"indicador": "porcentaje_grasa", "valor": 28.125}
					""")).andExpect(status().isCreated()).andExpect(header().exists("Location"));

			mvc.perform(titular(get(alta.andReturn().getResponse().getHeader("Location"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.notas").value("Buena adherencia al plan"))
					.andExpect(jsonPath("$.mediciones", hasSize(3)))
					.andExpect(jsonPath("$.mediciones[0].indicador").value("peso"))
					.andExpect(jsonPath("$.mediciones[0].valor").value(72.4))
					.andExpect(jsonPath("$.mediciones[0].unidad").value("kg"))
					.andExpect(jsonPath("$.mediciones[0].origen").value("MANUAL"))
					.andExpect(jsonPath("$.mediciones[2].valor").value(28.125));

			byte[] notas = enConsultorio(ana, () -> jdbc.sql("SELECT notas FROM consulta WHERE paciente_id = ?")
					.param(UUID.fromString(id)).query(byte[].class).single());
			assertThat(new String(notas, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("adherencia");
		}

		@Test
		void noSePuedeRepetirElMismoIndicadorEnUnaConsulta() throws Exception {
			String id = paciente(ana, true);

			consulta(ana, id, conMediciones(null, "{\"indicador\": \"peso\", \"valor\": 72}, {\"indicador\": \"peso\", \"valor\": 73}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores[0].campo").value("peso"));
		}

		@Test
		void laBaseTambienImpideRepetirElIndicador() throws Exception {
			String id = paciente(ana, true);
			String consulta = JsonPath.read(consulta(ana, id, conMediciones(null, "{\"indicador\": \"peso\", \"valor\": 72}"))
					.andReturn().getResponse().getContentAsString(), "$.id");

			assertThatThrownBy(() -> enConsultorio(ana, () -> jdbc.sql("""
					INSERT INTO medicion (id, tenant_id, consulta_id, indicador_id, valor, origen)
					SELECT gen_random_uuid(), ?, ?, id, 73, 'MANUAL' FROM indicador WHERE codigo = 'peso' AND tenant_id IS NULL
					""").params(ana.id(), UUID.fromString(consulta)).update()))
					.rootCause().hasMessageContaining("medicion_consulta_indicador_key");
		}

		@Test
		void rechazaValoresFueraDeRangoOConDemasiadosDecimales() throws Exception {
			String id = paciente(ana, true);

			consulta(ana, id, conMediciones(null, "{\"indicador\": \"peso\", \"valor\": 5000}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores[0].mensaje").value(org.hamcrest.Matchers.containsString("entre 0.5 y 500 kg")));
			consulta(ana, id, conMediciones(null, "{\"indicador\": \"talla\", \"valor\": 165.1234}"))
					.andExpect(status().isBadRequest());
			consulta(ana, id, conMediciones(null, "{\"indicador\": \"no_existe\", \"valor\": 1}"))
					.andExpect(status().isBadRequest());
		}

		@Test
		void unPacienteInexistenteODadoDeBajaResponde404() throws Exception {
			consulta(ana, UUID.randomUUID().toString(), conMediciones(null, "")).andExpect(status().isNotFound());

			String id = paciente(ana, true);
			mvc.perform(titular(delete("/api/pacientes/" + id))).andExpect(status().isNoContent());
			consulta(ana, id, conMediciones(null, "")).andExpect(status().isNotFound());
			mvc.perform(titular(get("/api/pacientes/%s/consultas".formatted(id)))).andExpect(status().isNotFound());
		}

		@Test
		void sinConsentimientoNoSeGuardanMediciones() throws Exception {
			String id = paciente(ana, false);

			consulta(ana, id, conMediciones(null, "{\"indicador\": \"peso\", \"valor\": 72}"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Consentimiento requerido"));
		}

		@Test
		void unaConsultaNoPuedeSerDelFuturo() throws Exception {
			consulta(ana, paciente(ana, true), conMediciones("2099-01-01T00:00:00Z", "")).andExpect(status().isBadRequest());
		}

	}

	@Nested
	class LineaDeTiempo {

		@Test
		void listaLasConsultasDeLaMasRecienteALaMasAntigua() throws Exception {
			String id = paciente(ana, true);
			consulta(ana, id, conMediciones("2026-08-01T10:00:00Z", "{\"indicador\": \"peso\", \"valor\": 75}"));
			consulta(ana, id, conMediciones("2026-10-01T10:00:00Z", "{\"indicador\": \"peso\", \"valor\": 72}"));
			consulta(ana, id, conMediciones("2026-09-01T10:00:00Z", "{\"indicador\": \"peso\", \"valor\": 73.5}"));

			mvc.perform(titular(get("/api/pacientes/%s/consultas".formatted(id))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[*].fecha", contains(
							"2026-10-01T10:00:00Z", "2026-09-01T10:00:00Z", "2026-08-01T10:00:00Z")))
					.andExpect(jsonPath("$.content[*].mediciones[0].valor", contains(72, 73.5, 75)));

			mvc.perform(titular(get("/api/pacientes/%s/consultas".formatted(id))).param("size", "2").param("page", "1"))
					.andExpect(jsonPath("$.content", hasSize(1)))
					.andExpect(jsonPath("$.page.totalElements").value(3));
		}

	}

	@Nested
	class AccesoYModulos {

		@Test
		void otroConsultorioNoVeLaConsulta() throws Exception {
			String deAna = JsonPath.read(consulta(ana, paciente(ana, true), conMediciones(null, ""))
					.andReturn().getResponse().getContentAsString(), "$.id");

			mvc.perform(como(consultorioNuevo(), get("/api/consultas/" + deAna), "TITULAR")).andExpect(status().isNotFound());
		}

		@Test
		void unaAsistenteNoVeConsultas() throws Exception {
			String id = paciente(ana, true);

			mvc.perform(como(ana, get("/api/pacientes/%s/consultas".formatted(id)), "ASISTENTE"))
					.andExpect(status().isForbidden());
		}

		@Test
		void conElModuloDeConsultasApagadoLasRutasNoExisten() throws Exception {
			String id = paciente(ana, true);
			mvc.perform(put("/api/operador/tenants/%s/modulos/consultations".formatted(ana.id()))
					.contentType(MediaType.APPLICATION_JSON).content("{\"habilitado\": false}")
					.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
					.andExpect(status().isOk());

			mvc.perform(titular(get("/api/pacientes/%s/consultas".formatted(id)))).andExpect(status().isNotFound());
			mvc.perform(titular(get("/api/indicadores"))).andExpect(status().isNotFound());
		}

	}

	@Test
	void registrarUnaConsultaQuedaEnLaBitacora() throws Exception {
		String consulta = JsonPath.read(consulta(ana, paciente(ana, true), conMediciones(null, "{\"indicador\": \"peso\", \"valor\": 70}"))
				.andReturn().getResponse().getContentAsString(), "$.id");

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(accionesSobre(UUID.fromString(consulta)))
				.containsExactly("CONSULTA_REGISTRADA"));
	}

	private List<String> accionesSobre(UUID recurso) {
		return enConsultorio(ana, () -> jdbc.sql("SELECT accion FROM auditoria WHERE recurso_id = ?")
				.param(recurso).query(String.class).list());
	}

}
