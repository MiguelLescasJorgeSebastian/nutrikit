package dev.jorgemiguel.nutrikit.patients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
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
 * Historia clínica cifrada de punta a punta: la API la devuelve en claro y en la base solo hay
 * bytes que no se pueden leer ni alterar sin que se note.
 */
@SpringBootTest(properties = "nutrikit.tenancy.cabecera-habilitada=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class HistoriaClinicaTest {

	private static final String ALERGIA = "Alergia grave a la penicilina";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	@Autowired
	private Environment entorno;

	private Consultorio ana;

	private record Consultorio(UUID id, String slug) {
	}

	@BeforeEach
	void preparar() throws Exception {
		ana = consultorioNuevo();
	}

	// --- utilidades ---------------------------------------------------------------------------

	/** Consultorio con su aviso de privacidad publicado. */
	private Consultorio consultorioNuevo() throws Exception {
		UUID id = UUID.randomUUID();
		String slug = "historia-" + id.toString().substring(0, 8);
		jdbc.sql("INSERT INTO tenant (id, slug, nombre) VALUES (?, ?, 'Consultorio')").params(id, slug).update();
		var consultorio = new Consultorio(id, slug);
		mvc.perform(como(consultorio, post("/api/avisos-de-privacidad"), "TITULAR").contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\": \"1\", \"contenido\": \"Aviso de privacidad de prueba\"}"))
				.andExpect(status().isCreated());
		return consultorio;
	}

	private MockHttpServletRequestBuilder como(Consultorio consultorio, MockHttpServletRequestBuilder peticion, String rol) {
		return peticion.header("X-Tenant", consultorio.slug())
				.with(jwt().jwt(t -> t.claim("tenant_id", consultorio.id().toString()).claim("rol", rol))
						.authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
	}

	private String paciente(Consultorio consultorio) throws Exception {
		String respuesta = mvc.perform(como(consultorio, post("/api/pacientes"), "TITULAR")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"nombre": "Ana", "apellidos": "Ruiz", "fechaNacimiento": "1990-01-01", "sexo": "FEMENINO"}
						"""))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(respuesta, "$.id");
		// Sin consentimiento no se puede guardar su historia clínica (ver ConsentimientoTest).
		mvc.perform(como(consultorio, post("/api/pacientes/%s/consentimientos".formatted(id)), "TITULAR")
				.contentType(MediaType.APPLICATION_JSON).content("{\"medio\": \"PRESENCIAL\"}"))
				.andExpect(status().isCreated());
		return id;
	}

	private ResultActions guardar(Consultorio consultorio, String pacienteId, String json) throws Exception {
		return mvc.perform(como(consultorio, put("/api/pacientes/%s/historia-clinica".formatted(pacienteId)), "TITULAR")
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions consultar(Consultorio consultorio, String pacienteId, String rol) throws Exception {
		return mvc.perform(como(consultorio, get("/api/pacientes/%s/historia-clinica".formatted(pacienteId)), rol));
	}

	private static String historia(String alergias, String antecedentes, Long version) {
		return """
				{"antecedentes": "%s", "padecimientos": "Hipotiroidismo", "medicamentos": "Levotiroxina 50 mcg",
				 "alergias": "%s", "camposExtra": {"Hábitos de sueño": "6 horas"}, "version": %s}
				""".formatted(antecedentes, alergias, version);
	}

	private <T> T enConsultorio(Consultorio consultorio, Supplier<T> accion) {
		return TenantContext.ejecutarComo(consultorio.id(), () -> tx.execute(estado -> accion.get()));
	}

	private Map<String, Object> filaCruda(Consultorio consultorio, String pacienteId) {
		return enConsultorio(consultorio, () -> jdbc.sql("SELECT * FROM historia_clinica WHERE paciente_id = ?")
				.param(UUID.fromString(pacienteId)).query().singleRow());
	}

	// --- pruebas ------------------------------------------------------------------------------

	@Test
	void laApiDevuelveLaHistoriaEnClaro() throws Exception {
		String id = paciente(ana);

		consultar(ana, id, "TITULAR").andExpect(status().isOk()).andExpect(jsonPath("$.version").doesNotExist());
		guardar(ana, id, historia(ALERGIA, "Madre con diabetes", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0));

		consultar(ana, id, "COLABORADOR")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alergias").value(ALERGIA))
				.andExpect(jsonPath("$.antecedentes").value("Madre con diabetes"))
				.andExpect(jsonPath("$.camposExtra['Hábitos de sueño']").value("6 horas"));
	}

	@Test
	void enLaBaseLosCamposSonBytesIlegibles() throws Exception {
		String id = paciente(ana);
		guardar(ana, id, historia(ALERGIA, "Madre con diabetes", null)).andExpect(status().isOk());

		Map<String, Object> fila = filaCruda(ana, id);

		for (String columna : List.of("antecedentes", "padecimientos", "medicamentos", "alergias", "campos_extra")) {
			byte[] valor = (byte[]) fila.get(columna);
			String comoTexto = new String(valor, StandardCharsets.UTF_8) + new String(valor, StandardCharsets.ISO_8859_1);
			assertThat(comoTexto).as(columna)
					.doesNotContain("penicilina", "diabetes", "Hipotiroidismo", "Levotiroxina", "sueño", "6 horas");
		}
	}

	@Test
	void cifrarElMismoTextoDosVecesDaResultadosDistintos() throws Exception {
		String id = paciente(ana);
		// El mismo texto en dos campos.
		guardar(ana, id, historia("Ninguna conocida", "Ninguna conocida", null)).andExpect(status().isOk());

		Map<String, Object> fila = filaCruda(ana, id);

		assertThat((byte[]) fila.get("alergias")).isNotEqualTo(fila.get("antecedentes"));
	}

	@Test
	void cadaValorIndicaLaVersionDeLlaveConQueSeCifro() throws Exception {
		String id = paciente(ana);
		guardar(ana, id, historia(ALERGIA, "x", null)).andExpect(status().isOk());

		byte[] alergias = (byte[]) filaCruda(ana, id).get("alergias");
		Map<String, Object> llave = enConsultorio(ana, () -> jdbc.sql(
				"SELECT version, llave_maestra_id, length(llave_envuelta) AS bytes FROM llave_de_datos").query().singleRow());

		assertThat(alergias[0]).as("formato").isEqualTo((byte) 1);
		assertThat(ByteBuffer.wrap(alergias, 1, 4).getInt()).as("versión de llave").isEqualTo(llave.get("version"));
		assertThat(llave).containsEntry("llave_maestra_id", "prueba").containsEntry("bytes", 12 + 32 + 16);
	}

	@Test
	void unDatoAlteradoEnLaBaseDaErrorNoBasura() throws Exception {
		String id = paciente(ana);
		guardar(ana, id, historia(ALERGIA, "x", null)).andExpect(status().isOk());

		// Alguien con acceso a la base cambia un byte del texto cifrado.
		enConsultorio(ana, () -> jdbc.sql("""
				UPDATE historia_clinica SET alergias = set_byte(alergias, 20, get_byte(alergias, 20) # 1)
				WHERE paciente_id = ?
				""").param(UUID.fromString(id)).update());

		consultar(ana, id, "TITULAR")
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.title").value("Dato cifrado inválido"));
	}

	@Test
	void unValorCopiadoDeOtroConsultorioNoSeDescifra() throws Exception {
		String deAna = paciente(ana);
		guardar(ana, deAna, historia(ALERGIA, "x", null)).andExpect(status().isOk());
		Consultorio beto = consultorioNuevo();
		String deBeto = paciente(beto);
		guardar(beto, deBeto, historia("Ninguna", "x", null)).andExpect(status().isOk());

		// Copia el valor cifrado de Ana a la historia de Beto.
		byte[] alergiaDeAna = (byte[]) filaCruda(ana, deAna).get("alergias");
		enConsultorio(beto, () -> jdbc.sql("UPDATE historia_clinica SET alergias = ? WHERE paciente_id = ?")
				.params(alergiaDeAna, UUID.fromString(deBeto)).update());

		consultar(beto, deBeto, "TITULAR").andExpect(status().isInternalServerError());
	}

	@Test
	void leerNoVuelveACifrarNiCambiaLaVersion() throws Exception {
		String id = paciente(ana);
		guardar(ana, id, historia(ALERGIA, "x", null)).andExpect(status().isOk());
		byte[] antes = (byte[]) filaCruda(ana, id).get("alergias");

		consultar(ana, id, "TITULAR").andExpect(jsonPath("$.version").value(0));
		consultar(ana, id, "TITULAR").andExpect(jsonPath("$.version").value(0));

		assertThat((byte[]) filaCruda(ana, id).get("alergias")).isEqualTo(antes);
	}

	@Test
	void editarConVersionViejaDevuelve409() throws Exception {
		String id = paciente(ana);
		guardar(ana, id, historia(ALERGIA, "x", null)).andExpect(status().isOk());
		guardar(ana, id, historia("Ninguna", "x", 0L)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

		guardar(ana, id, historia("Otra", "x", 0L)).andExpect(status().isConflict());
		guardar(ana, id, historia("Otra", "x", null)).andExpect(status().isConflict());
	}

	@Test
	void unaAsistenteNoVeLaHistoriaClinica() throws Exception {
		String id = paciente(ana);

		consultar(ana, id, "ASISTENTE").andExpect(status().isForbidden());
	}

	@Test
	void otroConsultorioNoLaVe() throws Exception {
		String deAna = paciente(ana);
		guardar(ana, deAna, historia(ALERGIA, "x", null)).andExpect(status().isOk());

		consultar(consultorioNuevo(), deAna, "TITULAR").andExpect(status().isNotFound());
	}

	@Test
	void cadaConsultaYCambioQuedaEnLaBitacora() throws Exception {
		String id = paciente(ana);
		guardar(ana, id, historia(ALERGIA, "x", null)).andExpect(status().isOk());
		consultar(ana, id, "TITULAR").andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(accionesSobreLaHistoria(id))
				.containsExactly("HISTORIA_CLINICA_ACTUALIZADA", "HISTORIA_CLINICA_CONSULTADA"));
	}

	private List<String> accionesSobreLaHistoria(String pacienteId) {
		return enConsultorio(ana, () -> jdbc.sql(
				"SELECT accion FROM auditoria WHERE recurso_tipo = 'HISTORIA_CLINICA' AND recurso_id = ? ORDER BY id")
				.param(UUID.fromString(pacienteId)).query(String.class).list());
	}

	@Test
	void laLlaveMaestraNoApareceEnLosLogs(CapturedOutput salida) throws Exception {
		String id = paciente(ana);
		guardar(ana, id, historia(ALERGIA, "x", null)).andExpect(status().isOk());
		consultar(ana, id, "TITULAR").andExpect(status().isOk());

		String llaveMaestra = entorno.getProperty("nutrikit.cifrado.llaves-maestras").split(":", 2)[1];
		assertThat(salida.getAll()).doesNotContain(llaveMaestra).doesNotContain(ALERGIA);
	}

}
