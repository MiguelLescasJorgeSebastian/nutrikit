package dev.jorgemiguel.nutrikit.patients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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

/** Aviso de privacidad y consentimiento: cada regla del issue con su prueba. */
@SpringBootTest(properties = "nutrikit.tenancy.cabecera-habilitada=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsentimientoTest {

	private static final UUID USUARIO = UUID.randomUUID();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	private UUID consultorio;
	private String slug;

	@BeforeEach
	void consultorioSinAviso() {
		consultorio = UUID.randomUUID();
		slug = "consentimiento-" + consultorio.toString().substring(0, 8);
		jdbc.sql("INSERT INTO tenant (id, slug, nombre) VALUES (?, ?, 'Consultorio')").params(consultorio, slug).update();
	}

	// --- utilidades ---------------------------------------------------------------------------

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion, String rol) {
		return peticion.header("X-Tenant", slug).contentType(MediaType.APPLICATION_JSON)
				.with(jwt().jwt(t -> t.subject(USUARIO.toString()).claim("tenant_id", consultorio.toString()).claim("rol", rol))
						.authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
	}

	private ResultActions publicarAviso(String version) throws Exception {
		return mvc.perform(como(post("/api/avisos-de-privacidad"), "TITULAR")
				.content("{\"version\": \"%s\", \"contenido\": \"Aviso versión %s\"}".formatted(version, version)));
	}

	private String paciente(String nombre) throws Exception {
		String respuesta = mvc.perform(como(post("/api/pacientes"), "ASISTENTE").content("""
				{"nombre": "%s", "apellidos": "Ruiz", "fechaNacimiento": "1990-01-01", "sexo": "FEMENINO"}
				""".formatted(nombre)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.id");
	}

	private ResultActions otorgar(String pacienteId) throws Exception {
		return mvc.perform(como(post("/api/pacientes/%s/consentimientos".formatted(pacienteId)), "ASISTENTE")
				.content("{\"medio\": \"PRESENCIAL\"}"));
	}

	private ResultActions retirar(String pacienteId) throws Exception {
		return mvc.perform(como(post("/api/pacientes/%s/consentimientos/retiro".formatted(pacienteId)), "TITULAR")
				.content("{\"medio\": \"EN_LINEA\"}"));
	}

	private ResultActions estado(String pacienteId) throws Exception {
		return mvc.perform(como(get("/api/pacientes/%s/consentimientos".formatted(pacienteId)), "TITULAR"));
	}

	private ResultActions guardarHistoria(String pacienteId) throws Exception {
		return mvc.perform(como(put("/api/pacientes/%s/historia-clinica".formatted(pacienteId)), "TITULAR")
				.content("{\"alergias\": \"Penicilina\"}"));
	}

	private <T> T enConsultorio(Supplier<T> accion) {
		return TenantContext.ejecutarComo(consultorio, () -> tx.execute(estado -> accion.get()));
	}

	// --- pruebas ------------------------------------------------------------------------------

	@Nested
	class HistoriaClinicaSinConsentimiento {

		@Test
		void guardarLaHistoriaSinConsentimientoResponde409ConUnMensajeClaro() throws Exception {
			publicarAviso("1").andExpect(status().isCreated());
			String id = paciente("Ana");

			guardarHistoria(id)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Consentimiento requerido"))
					.andExpect(jsonPath("$.detail", containsString("consentimiento vigente")));
		}

		@Test
		void conConsentimientoSePuedeGuardar() throws Exception {
			publicarAviso("1").andExpect(status().isCreated());
			String id = paciente("Ana");
			otorgar(id).andExpect(status().isCreated());

			guardarHistoria(id).andExpect(status().isOk());
		}

		@Test
		void trasRetirarloYaNoSePuedeGuardarPeroLoGuardadoSeConserva() throws Exception {
			publicarAviso("1").andExpect(status().isCreated());
			String id = paciente("Ana");
			otorgar(id).andExpect(status().isCreated());
			guardarHistoria(id).andExpect(status().isOk());

			retirar(id).andExpect(status().isCreated());

			guardarHistoria(id).andExpect(status().isConflict());
			mvc.perform(como(get("/api/pacientes/%s/historia-clinica".formatted(id)), "TITULAR"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.alergias").value("Penicilina"));
		}

	}

	@Nested
	class Historial {

		@Test
		void elRetiroNoBorraElOtorgamientoYTodoQuedaConsultable() throws Exception {
			publicarAviso("1").andExpect(status().isCreated());
			String id = paciente("Ana");
			otorgar(id).andExpect(status().isCreated());
			retirar(id).andExpect(status().isCreated());
			otorgar(id).andExpect(status().isCreated());

			estado(id).andExpect(status().isOk())
					.andExpect(jsonPath("$.vigente").value(true))
					.andExpect(jsonPath("$.historial[*].accion", contains("OTORGADO", "RETIRADO", "OTORGADO")))
					.andExpect(jsonPath("$.historial[1].medio").value("EN_LINEA"))
					.andExpect(jsonPath("$.historial[0].registradoPor").value(USUARIO.toString()));
		}

		@Test
		void laAppNoPuedeModificarNiBorrarConsentimientos() throws Exception {
			publicarAviso("1").andExpect(status().isCreated());
			String id = paciente("Ana");
			otorgar(id).andExpect(status().isCreated());

			assertThatThrownBy(() -> enConsultorio(() -> jdbc.sql("UPDATE consentimiento SET accion = 'RETIRADO'").update()))
					.rootCause().hasMessageContaining("permission denied for table consentimiento");
			assertThatThrownBy(() -> enConsultorio(() -> jdbc.sql("DELETE FROM consentimiento").update()))
					.rootCause().hasMessageContaining("permission denied for table consentimiento");
			assertThatThrownBy(() -> enConsultorio(() -> jdbc.sql("UPDATE aviso_de_privacidad SET contenido = 'otro'").update()))
					.rootCause().hasMessageContaining("permission denied for table aviso_de_privacidad");
		}

		@Test
		void noSeRetiraLoQueNoEstaVigenteNiSeAceptaDosVecesLaMismaVersion() throws Exception {
			publicarAviso("1").andExpect(status().isCreated());
			String id = paciente("Ana");

			retirar(id).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Sin consentimiento vigente"));
			otorgar(id).andExpect(status().isCreated());
			otorgar(id).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Consentimiento ya vigente"));
		}

		@Test
		void sinAvisoPublicadoNoSePuedeOtorgar() throws Exception {
			String id = paciente("Ana");

			otorgar(id).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Sin aviso de privacidad"));
			mvc.perform(como(get("/api/avisos-de-privacidad/vigente"), "TITULAR")).andExpect(status().isNotFound());
		}

		@Test
		void elMedioEsObligatorio() throws Exception {
			publicarAviso("1").andExpect(status().isCreated());
			String id = paciente("Ana");

			mvc.perform(como(post("/api/pacientes/%s/consentimientos".formatted(id)), "TITULAR").content("{}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores[0].campo").value("medio"));
		}

	}

	@Nested
	class VersionesDelAviso {

		@Test
		void alPublicarUnaVersionNuevaSeSabeQuienAceptoUnaAnterior() throws Exception {
			publicarAviso("2026.1").andExpect(status().isCreated());
			String ana = paciente("Ana");
			String beto = paciente("Beto");
			String carla = paciente("Carla");
			otorgar(ana).andExpect(status().isCreated());
			otorgar(beto).andExpect(status().isCreated());
			otorgar(carla).andExpect(status().isCreated());
			retirar(carla).andExpect(status().isCreated());

			publicarAviso("2026.2").andExpect(status().isCreated());
			otorgar(beto).andExpect(status().isCreated()).andExpect(jsonPath("$.versionAviso").value("2026.2"));

			// Ana aceptó la anterior; Beto ya aceptó la nueva; Carla retiró el suyo.
			mvc.perform(como(get("/api/consentimientos/desactualizados"), "COLABORADOR"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$[0].pacienteId").value(ana))
					.andExpect(jsonPath("$[0].versionAviso").value("2026.1"));
			estado(ana).andExpect(jsonPath("$.vigente").value(true)).andExpect(jsonPath("$.desactualizado").value(true));
			estado(beto).andExpect(jsonPath("$.desactualizado").value(false));

			// Un consentimiento de la versión anterior sigue vigente: su historia se puede guardar.
			guardarHistoria(ana).andExpect(status().isOk());
		}

		@Test
		void unaVersionPublicadaNoSeRepiteYSoloLaTitularPublica() throws Exception {
			publicarAviso("1").andExpect(status().isCreated());

			publicarAviso("1").andExpect(status().isConflict());
			mvc.perform(como(post("/api/avisos-de-privacidad"), "COLABORADOR")
					.content("{\"version\": \"2\", \"contenido\": \"x\"}"))
					.andExpect(status().isForbidden());
			mvc.perform(como(get("/api/avisos-de-privacidad/vigente"), "ASISTENTE"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.version").value("1"));
		}

	}

	@Test
	void otroConsultorioNoVeElConsentimiento() throws Exception {
		publicarAviso("1").andExpect(status().isCreated());
		String deAna = paciente("Ana");
		otorgar(deAna).andExpect(status().isCreated());
		UUID otro = UUID.randomUUID();
		String otroSlug = "consentimiento-" + otro.toString().substring(0, 8);
		jdbc.sql("INSERT INTO tenant (id, slug, nombre) VALUES (?, ?, 'Otro')").params(otro, otroSlug).update();

		mvc.perform(get("/api/pacientes/%s/consentimientos".formatted(deAna)).header("X-Tenant", otroSlug)
				.with(jwt().jwt(t -> t.claim("tenant_id", otro.toString()).claim("rol", "TITULAR"))
						.authorities(new SimpleGrantedAuthority("ROLE_TITULAR"))))
				.andExpect(status().isNotFound());
	}

	@Test
	void otorgarRetirarYPublicarQuedanEnLaBitacora() throws Exception {
		publicarAviso("1").andExpect(status().isCreated());
		String id = paciente("Ana");
		otorgar(id).andExpect(status().isCreated());
		retirar(id).andExpect(status().isCreated());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(acciones())
				.contains("AVISO_PRIVACIDAD_PUBLICADO", "CONSENTIMIENTO_OTORGADO", "CONSENTIMIENTO_RETIRADO"));
	}

	private List<String> acciones() {
		return enConsultorio(() -> jdbc.sql("SELECT accion FROM auditoria ORDER BY id").query(String.class).list());
	}

}
