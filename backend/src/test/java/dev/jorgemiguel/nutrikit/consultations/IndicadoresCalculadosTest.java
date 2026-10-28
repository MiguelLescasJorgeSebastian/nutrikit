package dev.jorgemiguel.nutrikit.consultations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
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

/**
 * Indicadores calculados al guardar y al corregir una consulta. El paciente es un hombre que el día
 * de la consulta (1 de octubre de 2026) tiene 30 años: los mismos datos que las pruebas unitarias
 * de las fórmulas, así los resultados esperados son los de referencia.
 */
@SpringBootTest(properties = "nutrikit.tenancy.cabecera-habilitada=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IndicadoresCalculadosTest {

	private static final String FECHA = "2026-10-01T10:00:00Z";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	private UUID consultorio;
	private String slug;
	private String paciente;

	@BeforeEach
	void preparar() throws Exception {
		slug = "calculos-" + UUID.randomUUID().toString().substring(0, 8);
		String respuesta = mvc.perform(post("/api/operador/tenants").contentType(MediaType.APPLICATION_JSON)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
				.content("{\"slug\": \"%s\", \"nombre\": \"Consultorio\"}".formatted(slug)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		consultorio = UUID.fromString(JsonPath.read(respuesta, "$.id"));
		mvc.perform(como(post("/api/avisos-de-privacidad"), "TITULAR").content("{\"version\": \"1\", \"contenido\": \"Aviso\"}"))
				.andExpect(status().isCreated());
		paciente = JsonPath.read(mvc.perform(como(post("/api/pacientes"), "TITULAR").content("""
				{"nombre": "Luis", "apellidos": "Ruiz", "fechaNacimiento": "1996-01-15", "sexo": "MASCULINO"}
				""")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(como(post("/api/pacientes/%s/consentimientos".formatted(paciente)), "TITULAR")
				.content("{\"medio\": \"PRESENCIAL\"}")).andExpect(status().isCreated());
	}

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion, String rol) {
		return peticion.header("X-Tenant", slug).contentType(MediaType.APPLICATION_JSON)
				.with(jwt().jwt(t -> t.claim("tenant_id", consultorio.toString()).claim("rol", rol))
						.authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
	}

	private ResultActions consulta(String mediciones) throws Exception {
		return mvc.perform(como(post("/api/pacientes/%s/consultas".formatted(paciente)), "TITULAR")
				.content("{\"fecha\": \"%s\", \"mediciones\": [%s]}".formatted(FECHA, mediciones)));
	}

	private String idDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions corregir(String consultaId, String indicador, String valor) throws Exception {
		return mvc.perform(como(put("/api/consultas/%s/mediciones/%s".formatted(consultaId, indicador)), "TITULAR")
				.content("{\"valor\": %s}".formatted(valor)));
	}

	private static String calculado(String codigo) {
		return "$.mediciones[?(@.indicador == '%s' && @.origen == 'CALCULADO')].valor".formatted(codigo);
	}

	private static final String COMPLETAS = """
			{"indicador": "peso", "valor": 70}, {"indicador": "talla", "valor": 175},
			{"indicador": "circunferencia_cintura", "valor": 94}, {"indicador": "circunferencia_cadera", "valor": 102}
			""";

	@Test
	void alGuardarCalculaLosDerivadosConSusDatosDisponibles() throws Exception {
		consulta(COMPLETAS)
				.andExpect(status().isCreated())
				.andExpect(jsonPath(calculado("imc")).value(22.86))
				.andExpect(jsonPath(calculado("indice_cintura_cadera")).value(0.92))
				// Mifflin-St Jeor, la fórmula por defecto: 1648.75 → 1649.
				.andExpect(jsonPath(calculado("gasto_energetico_basal")).value(1649));
	}

	@Test
	void siFaltaUnDatoEseIndicadorNoSeCalculaYNoHayError() throws Exception {
		consulta("""
				{"indicador": "peso", "valor": 70},
				{"indicador": "circunferencia_cintura", "valor": 94}, {"indicador": "circunferencia_cadera", "valor": 102}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath(calculado("indice_cintura_cadera")).value(0.92))
				.andExpect(jsonPath(calculado("imc"), empty()))
				.andExpect(jsonPath(calculado("gasto_energetico_basal"), empty()));
	}

	@Test
	void unIndicadorCalculadoNoSeCapturaNiSeCorrigeAMano() throws Exception {
		consulta("{\"indicador\": \"imc\", \"valor\": 25}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores[0].mensaje").value("se calcula automáticamente; no se captura a mano"));

		String id = idDe(consulta(COMPLETAS));
		corregir(id, "imc", "30").andExpect(status().isBadRequest());
	}

	@Test
	void corregirUnaEntradaRecalculaSusDerivados() throws Exception {
		String id = idDe(consulta(COMPLETAS));

		corregir(id, "peso", "80")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mediciones[?(@.indicador == 'peso')].valor").value(80))
				// 80 / 1.75² = 26.122 → 26.12; Mifflin: 800 + 1093.75 − 150 + 5 = 1748.75 → 1749.
				.andExpect(jsonPath(calculado("imc")).value(26.12))
				.andExpect(jsonPath(calculado("gasto_energetico_basal")).value(1749))
				.andExpect(jsonPath(calculado("indice_cintura_cadera")).value(0.92));

		// Persistido, no solo en la respuesta.
		mvc.perform(como(get("/api/consultas/" + id), "COLABORADOR")).andExpect(jsonPath(calculado("imc")).value(26.12));
	}

	@Test
	void corregirValidaElRangoYQueLaMedicionExista() throws Exception {
		String id = idDe(consulta("{\"indicador\": \"peso\", \"valor\": 70}"));

		corregir(id, "peso", "5000").andExpect(status().isBadRequest());
		corregir(id, "talla", "170").andExpect(status().isNotFound());
		corregir(id, "no_existe", "1").andExpect(status().isBadRequest());
	}

	@Test
	void laFormulaDeGastoEnergeticoSeEligePorConsultorio() throws Exception {
		mvc.perform(como(get("/api/configuracion/gasto-energetico"), "COLABORADOR"))
				.andExpect(jsonPath("$.formula").value("MIFFLIN_ST_JEOR"));
		mvc.perform(como(put("/api/configuracion/gasto-energetico"), "COLABORADOR")
				.content("{\"formula\": \"HARRIS_BENEDICT\"}")).andExpect(status().isForbidden());

		mvc.perform(como(put("/api/configuracion/gasto-energetico"), "TITULAR")
				.content("{\"formula\": \"HARRIS_BENEDICT\"}")).andExpect(status().isOk());

		// Harris-Benedict revisada: 1695.667 → 1696.
		consulta(COMPLETAS).andExpect(jsonPath(calculado("gasto_energetico_basal")).value(1696));
	}

	@Test
	void laAppSoloPuedeCambiarElValorDeUnaMedicion() throws Exception {
		consulta(COMPLETAS).andExpect(status().isCreated());

		assertThatThrownBy(() -> enConsultorio(() -> jdbc.sql("UPDATE medicion SET origen = 'MANUAL'").update()))
				.rootCause().hasMessageContaining("permission denied for table medicion");
		assertThatThrownBy(() -> enConsultorio(() -> jdbc.sql("DELETE FROM medicion").update()))
				.rootCause().hasMessageContaining("permission denied for table medicion");
	}

	@Test
	void laCorreccionQuedaEnLaBitacora() throws Exception {
		String id = idDe(consulta(COMPLETAS));
		corregir(id, "peso", "71").andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(acciones(UUID.fromString(id)))
				.contains("CONSULTA_REGISTRADA", "MEDICION_CORREGIDA"));
	}

	private List<String> acciones(UUID consulta) {
		return enConsultorio(() -> jdbc.sql("SELECT accion FROM auditoria WHERE recurso_id = ? ORDER BY id")
				.param(consulta).query(String.class).list());
	}

	private <T> T enConsultorio(Supplier<T> accion) {
		return TenantContext.ejecutarComo(consultorio, () -> tx.execute(estado -> accion.get()));
	}

}
