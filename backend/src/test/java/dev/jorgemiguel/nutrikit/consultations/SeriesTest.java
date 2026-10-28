package dev.jorgemiguel.nutrikit.consultations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

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

/**
 * Series de evolución y comparación de consultas. Los datos de 5 años se generan con SQL (más rápido
 * que 260 peticiones), dentro del consultorio, como lo haría la app.
 */
@SpringBootTest(properties = { "nutrikit.tenancy.cabecera-habilitada=true", "springdoc.api-docs.enabled=true" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ContadorDeSentenciasSql.class })
class SeriesTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	private UUID consultorio;
	private String slug;

	@BeforeEach
	void consultorio() throws Exception {
		slug = "series-" + UUID.randomUUID().toString().substring(0, 8);
		String respuesta = mvc.perform(post("/api/operador/tenants").contentType(MediaType.APPLICATION_JSON)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
				.content("{\"slug\": \"%s\", \"nombre\": \"Consultorio\"}".formatted(slug)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		consultorio = UUID.fromString(JsonPath.read(respuesta, "$.id"));
		mvc.perform(como(post("/api/avisos-de-privacidad")).content("{\"version\": \"1\", \"contenido\": \"Aviso\"}"))
				.andExpect(status().isCreated());
	}

	// --- utilidades ---------------------------------------------------------------------------

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion) {
		return peticion.header("X-Tenant", slug).contentType(MediaType.APPLICATION_JSON)
				.with(jwt().jwt(t -> t.claim("tenant_id", consultorio.toString()).claim("rol", "TITULAR"))
						.authorities(new SimpleGrantedAuthority("ROLE_TITULAR")));
	}

	private String paciente() throws Exception {
		String id = JsonPath.read(mvc.perform(como(post("/api/pacientes")).content("""
				{"nombre": "Ana", "apellidos": "Ruiz", "fechaNacimiento": "1990-01-01", "sexo": "FEMENINO"}
				""")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(como(post("/api/pacientes/%s/consentimientos".formatted(id))).content("{\"medio\": \"PRESENCIAL\"}"))
				.andExpect(status().isCreated());
		return id;
	}

	/**
	 * Consultas semanales desde el 1 de noviembre de 2021 con peso (capturado), talla (capturada) e
	 * IMC (calculado). El peso baja 50 g por semana.
	 */
	private void consultasSemanales(String pacienteId, int semanas) {
		TenantContext.ejecutarComo(consultorio, () -> tx.executeWithoutResult(estado -> {
			jdbc.sql("""
					INSERT INTO consulta (id, tenant_id, paciente_id, fecha, creado_en)
					SELECT gen_random_uuid(), ?, ?, TIMESTAMPTZ '2021-11-01 16:00:00+00' + make_interval(weeks => n), now()
					FROM generate_series(0, ? - 1) AS n
					""").params(consultorio, UUID.fromString(pacienteId), semanas).update();
			jdbc.sql("""
					INSERT INTO medicion (id, tenant_id, consulta_id, indicador_id, valor, origen)
					SELECT gen_random_uuid(), c.tenant_id, c.id, i.id,
					       CASE i.codigo WHEN 'peso' THEN 90 - 0.05 * row_number() OVER (PARTITION BY i.id ORDER BY c.fecha)
					                     WHEN 'talla' THEN 170
					                     ELSE (90 - 0.05 * row_number() OVER (PARTITION BY i.id ORDER BY c.fecha)) / 2.89 END,
					       CASE WHEN i.calculado THEN 'CALCULADO' ELSE 'MANUAL' END
					FROM consulta c CROSS JOIN indicador i
					WHERE c.paciente_id = ? AND i.tenant_id IS NULL AND i.codigo IN ('peso', 'talla', 'imc')
					""").param(UUID.fromString(pacienteId)).update();
		}));
	}

	private ResultActions series(String pacienteId, String parametros) throws Exception {
		return mvc.perform(como(get("/api/pacientes/%s/series%s".formatted(pacienteId, parametros))));
	}

	private String consulta(String pacienteId, String fecha, String mediciones) throws Exception {
		return JsonPath.read(mvc.perform(como(post("/api/pacientes/%s/consultas".formatted(pacienteId)))
				.content("{\"fecha\": \"%s\", \"mediciones\": [%s]}".formatted(fecha, mediciones)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	// --- pruebas ------------------------------------------------------------------------------

	@Nested
	class Series {

		@Test
		void cincoAniosDeConsultasSemanalesRespondenEnMenosDe300Ms() throws Exception {
			String id = paciente();
			consultasSemanales(id, 260);
			series(id, "?indicadores=peso,imc").andExpect(status().isOk()); // calentamiento

			long inicio = System.nanoTime();
			var respuesta = series(id, "?indicadores=peso,imc").andExpect(status().isOk());
			Duration tiempo = Duration.ofNanos(System.nanoTime() - inicio);

			assertThat(tiempo).isLessThan(Duration.ofMillis(300));
			respuesta.andExpect(jsonPath("$.series", hasSize(2)))
					.andExpect(jsonPath("$.series[0].indicador").value("imc"))
					.andExpect(jsonPath("$.series[0].calculado").value(true))
					.andExpect(jsonPath("$.series[0].unidad").value("kg/m²"))
					.andExpect(jsonPath("$.series[0].puntos", hasSize(260)))
					.andExpect(jsonPath("$.series[0].puntos[0].origen").value("CALCULADO"))
					.andExpect(jsonPath("$.series[1].indicador").value("peso"))
					.andExpect(jsonPath("$.series[1].unidad").value("kg"))
					.andExpect(jsonPath("$.series[1].puntos", hasSize(260)))
					.andExpect(jsonPath("$.series[1].puntos[0].fecha").value("2021-11-01T16:00:00Z"))
					.andExpect(jsonPath("$.series[1].puntos[0].valor").value(89.95))
					.andExpect(jsonPath("$.series[1].puntos[0].origen").value("MANUAL"))
					.andExpect(jsonPath("$.series[1].puntos[259].fecha").value("2026-10-19T16:00:00Z"));
		}

		@Test
		void filtraPorRangoDeFechasInclusivo() throws Exception {
			String id = paciente();
			consultasSemanales(id, 10);

			series(id, "?indicadores=peso&desde=2021-11-08&hasta=2021-11-22")
					.andExpect(jsonPath("$.series[0].puntos[*].fecha", contains(
							"2021-11-08T16:00:00Z", "2021-11-15T16:00:00Z", "2021-11-22T16:00:00Z")));
		}

		@Test
		void sinIndicadoresDevuelveTodosLosQueTienenDatos() throws Exception {
			String id = paciente();
			consultasSemanales(id, 3);

			series(id, "").andExpect(jsonPath("$.series[*].indicador", contains("imc", "peso", "talla")));
		}

		@Test
		void unIndicadorSinDatosDevuelveUnaSerieVacia() throws Exception {
			String id = paciente();
			consultasSemanales(id, 3);

			series(id, "?indicadores=peso,porcentaje_grasa")
					.andExpect(jsonPath("$.series[?(@.indicador == 'porcentaje_grasa')].puntos[*]", hasSize(0)))
					.andExpect(jsonPath("$.series[?(@.indicador == 'porcentaje_grasa')].unidad").value("%"));
		}

		@Test
		void validaLosParametros() throws Exception {
			String id = paciente();

			series(id, "?indicadores=no_existe").andExpect(status().isBadRequest());
			series(id, "?desde=2026-02-01&hasta=2026-01-01").andExpect(status().isBadRequest());
			series(id, "?indicadores=a,b,c,d,e,f,g,h,i,j,k").andExpect(status().isBadRequest());
		}

		@Test
		void unPacienteDadoDeBajaODeOtroConsultorioResponde404() throws Exception {
			String id = paciente();
			series(UUID.randomUUID().toString(), "").andExpect(status().isNotFound());

			mvc.perform(como(delete("/api/pacientes/" + id))).andExpect(status().isNoContent());
			series(id, "").andExpect(status().isNotFound());
		}

	}

	@Nested
	class SinNMasUno {

		private int sentenciasDeUnaSerie(String pacienteId, String indicadores) throws Exception {
			ContadorDeSentenciasSql.reiniciar();
			series(pacienteId, "?indicadores=" + indicadores).andExpect(status().isOk());
			return ContadorDeSentenciasSql.sentencias();
		}

		/**
		 * N+1 haría crecer las consultas SQL con el número de consultas o de indicadores. Aquí el
		 * conteo es el mismo con 10 que con 260 consultas, y con 1 que con 3 indicadores.
		 */
		@Test
		void elNumeroDeConsultasSqlNoCreceConLosDatos() throws Exception {
			String pocas = paciente();
			consultasSemanales(pocas, 10);
			String muchas = paciente();
			consultasSemanales(muchas, 260);
			sentenciasDeUnaSerie(pocas, "peso"); // calentamiento: cachés de Spring e Hibernate

			int conPocas = sentenciasDeUnaSerie(pocas, "peso");
			int conMuchas = sentenciasDeUnaSerie(muchas, "peso");
			int conTresIndicadores = sentenciasDeUnaSerie(muchas, "peso,talla,imc");

			assertThat(conMuchas).isEqualTo(conPocas);
			assertThat(conTresIndicadores).isEqualTo(conPocas);
			// Consultorio, paciente activo, indicadores, set_config de RLS y la serie: unas cuantas, fijas.
			assertThat(conPocas).isLessThanOrEqualTo(8);
		}

	}

	@Nested
	class Comparacion {

		@Test
		void calculaLaDiferenciaAbsolutaYPorcentualPorIndicador() throws Exception {
			String id = paciente();
			String agosto = consulta(id, "2026-08-01T16:00:00Z", """
					{"indicador": "peso", "valor": 82.5}, {"indicador": "talla", "valor": 175},
					{"indicador": "porcentaje_grasa", "valor": 31.2}
					""");
			String septiembre = consulta(id, "2026-09-01T16:00:00Z", """
					{"indicador": "peso", "valor": 80}, {"indicador": "talla", "valor": 175}
					""");

			// El orden de la petición no importa: siempre posterior − anterior.
			mvc.perform(como(get("/api/consultas/%s/comparacion/%s".formatted(septiembre, agosto))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.consultaAnterior").value(agosto))
					.andExpect(jsonPath("$.consultaPosterior").value(septiembre))
					.andExpect(jsonPath("$.indicadores[?(@.indicador == 'peso')].diferencia").value(-2.5))
					.andExpect(jsonPath("$.indicadores[?(@.indicador == 'peso')].porcentaje").value(-3.0))
					.andExpect(jsonPath("$.indicadores[?(@.indicador == 'talla')].diferencia").value(0))
					// IMC calculado: 26.94 → 26.12.
					.andExpect(jsonPath("$.indicadores[?(@.indicador == 'imc')].diferencia").value(-0.82))
					.andExpect(jsonPath("$.indicadores[?(@.indicador == 'imc')].porcentaje").value(-3.0))
					// Solo en la anterior: sin diferencia.
					.andExpect(jsonPath("$.indicadores[?(@.indicador == 'porcentaje_grasa')].valorAnterior").value(31.2))
					.andExpect(jsonPath("$.indicadores[?(@.indicador == 'porcentaje_grasa')].diferencia", contains(nullValue())));
		}

		@Test
		void soloComparaDosConsultasDistintasDelMismoPaciente() throws Exception {
			String ana = paciente();
			String beto = paciente();
			String deAna = consulta(ana, "2026-08-01T16:00:00Z", "{\"indicador\": \"peso\", \"valor\": 70}");
			String deBeto = consulta(beto, "2026-08-02T16:00:00Z", "{\"indicador\": \"peso\", \"valor\": 80}");

			mvc.perform(como(get("/api/consultas/%s/comparacion/%s".formatted(deAna, deBeto)))).andExpect(status().isBadRequest());
			mvc.perform(como(get("/api/consultas/%s/comparacion/%s".formatted(deAna, deAna)))).andExpect(status().isBadRequest());
			mvc.perform(como(get("/api/consultas/%s/comparacion/%s".formatted(deAna, UUID.randomUUID()))))
					.andExpect(status().isNotFound());
		}

	}

	@Test
	void elContratoEstaDocumentadoEnOpenApiConUnEjemplo() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"/api/pacientes/{pacienteId}/series\"")))
				.andExpect(content().string(containsString("\"/api/consultas/{consultaId}/comparacion/{otraConsultaId}\"")))
				.andExpect(content().string(containsString("Peso e IMC")))
				.andExpect(content().string(containsString("Índice de masa corporal")));
	}

}
