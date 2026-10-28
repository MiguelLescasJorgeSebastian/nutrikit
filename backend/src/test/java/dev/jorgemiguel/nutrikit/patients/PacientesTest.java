package dev.jorgemiguel.nutrikit.patients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.patients.domain.DatosPersonales;
import dev.jorgemiguel.nutrikit.patients.domain.Paciente;
import dev.jorgemiguel.nutrikit.patients.domain.PacienteRepository;
import dev.jorgemiguel.nutrikit.patients.domain.Sexo;
import dev.jorgemiguel.nutrikit.shared.TenantContext;

/**
 * Pacientes de punta a punta contra Postgres, conectados como el usuario de la app.
 *
 * <p>Los consultorios no se borran al terminar: {@code paciente} no admite {@code DELETE} (la
 * baja es lógica) y su llave foránea protege al consultorio. Cada prueba usa un consultorio con
 * slug aleatorio.
 */
@SpringBootTest(properties = "nutrikit.tenancy.cabecera-habilitada=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PacientesTest {

	private static final String RUTA = "/api/pacientes";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate tx;

	@Autowired
	private PacienteRepository repository;

	private UUID consultorio;
	private String slug;

	@BeforeEach
	void consultorio() {
		consultorio = UUID.randomUUID();
		slug = "pacientes-" + consultorio.toString().substring(0, 8);
		jdbc.sql("INSERT INTO tenant (id, slug, nombre) VALUES (?, ?, 'Consultorio de pruebas')")
				.params(consultorio, slug).update();
	}

	// --- utilidades ---------------------------------------------------------------------------

	private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder peticion, String rol) {
		return peticion.header("X-Tenant", slug)
				.with(jwt().jwt(t -> t.claim("tenant_id", consultorio.toString()).claim("rol", rol))
						.authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
	}

	private MockHttpServletRequestBuilder titular(MockHttpServletRequestBuilder peticion) {
		return como(peticion, "TITULAR");
	}

	private static String cuerpo(String nombre, String apellidos, String telefono, Long version) {
		return """
				{"nombre": "%s", "apellidos": "%s", "fechaNacimiento": "1988-04-17", "sexo": "MASCULINO",
				 "telefono": %s, "email": "paciente@correo.mx", "ocupacion": "Contador", "version": %s}
				""".formatted(nombre, apellidos, telefono == null ? "null" : "\"" + telefono + "\"", version);
	}

	private ResultActions registrar(String nombre, String apellidos, String telefono) throws Exception {
		return mvc.perform(titular(post(RUTA)).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(nombre, apellidos, telefono, null)));
	}

	private String idDe(ResultActions resultado) throws Exception {
		return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private ResultActions buscar(String q) throws Exception {
		var peticion = titular(get(RUTA)).param("size", "100");
		if (q != null) {
			peticion.param("q", q);
		}
		return mvc.perform(peticion);
	}

	// --- pruebas ------------------------------------------------------------------------------

	@Nested
	class AltaYConsulta {

		@Test
		void registraUnPacienteYCalculaSuEdad() throws Exception {
			var alta = registrar("  José   Luis ", "Pérez García", "(55) 1234-5678")
					.andExpect(status().isCreated())
					.andExpect(header().exists("Location"))
					.andExpect(jsonPath("$.nombre").value("José Luis"))
					.andExpect(jsonPath("$.telefono").value("5512345678"))
					.andExpect(jsonPath("$.version").value(0));

			int edadEsperada = java.time.Period.between(LocalDate.of(1988, 4, 17), LocalDate.now(ZoneOffset.UTC)).getYears();
			mvc.perform(titular(get(alta.andReturn().getResponse().getHeader("Location"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.edad").value(edadEsperada))
					.andExpect(jsonPath("$.fechaNacimiento").value("1988-04-17"));
		}

		@Test
		void laEdadNoSeGuardaEnLaBase() {
			List<String> columnas = jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name = 'paciente'")
					.query(String.class).list();

			assertThat(columnas).contains("fecha_nacimiento").doesNotContain("edad");
		}

		@Test
		void datosInvalidosDevuelven400PorCampo() throws Exception {
			mvc.perform(titular(post(RUTA)).contentType(MediaType.APPLICATION_JSON).content("""
					{"nombre": "", "apellidos": "Pérez", "fechaNacimiento": "1988-04-17"}
					"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores[*].campo", containsInAnyOrder("nombre", "sexo")));

			registrar("Ana", "Pérez", "123").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores[0].campo").value("telefono"));
		}

	}

	@Nested
	class Busqueda {

		@Test
		void buscarJoseEncuentraAJoseConAcento() throws Exception {
			registrar("José", "Pérez", null).andExpect(status().isCreated());
			registrar("María José", "López", null).andExpect(status().isCreated());
			registrar("Ana", "Joséfina", null).andExpect(status().isCreated());
			registrar("Pedro", "Ramírez", null).andExpect(status().isCreated());

			buscar("jose").andExpect(status().isOk())
					.andExpect(jsonPath("$.content[*].nombre", containsInAnyOrder("José", "María José", "Ana")));
			buscar("JOSÉ").andExpect(jsonPath("$.content", hasSize(3)));
		}

		@Test
		void cadaPalabraDebeAparecer() throws Exception {
			registrar("José", "Pérez García", null).andExpect(status().isCreated());
			registrar("José", "López", null).andExpect(status().isCreated());

			buscar("jose perez").andExpect(jsonPath("$.content", hasSize(1)))
					.andExpect(jsonPath("$.content[0].apellidos").value("Pérez García"));
		}

		@Test
		void buscaPorTelefono() throws Exception {
			registrar("Ana", "Ruiz", "(55) 9876-5432").andExpect(status().isCreated());

			buscar("98765432").andExpect(jsonPath("$.content", hasSize(1)));
		}

		@Test
		void losComodinesSeBuscanComoTexto() throws Exception {
			registrar("Ana", "Ruiz", null).andExpect(status().isCreated());

			buscar("%").andExpect(jsonPath("$.content", hasSize(0)));
			buscar("_").andExpect(jsonPath("$.content", hasSize(0)));
		}

		@Test
		void sinTextoListaEnOrdenAlfabeticoPorApellido() throws Exception {
			registrar("Zoe", "Álvarez", null).andExpect(status().isCreated());
			registrar("Ana", "Zamora", null).andExpect(status().isCreated());
			registrar("Beto", "Martínez", null).andExpect(status().isCreated());

			buscar(null).andExpect(jsonPath("$.content[*].apellidos").value(
					org.hamcrest.Matchers.contains("Álvarez", "Martínez", "Zamora")));
		}

		@Test
		void buscarEntre500PacientesTardaMenosDeUnSegundo() throws Exception {
			TenantContext.ejecutarComo(consultorio, () -> tx.executeWithoutResult(estado -> jdbc.sql("""
					INSERT INTO paciente (id, tenant_id, nombre, apellidos, fecha_nacimiento, sexo, telefono, creado_en)
					SELECT gen_random_uuid(), ?,
					       (ARRAY['José','María','Ana','Luis','Sofía','Andrés','Lucía','Raúl'])[1 + n % 8],
					       (ARRAY['Pérez','Gómez','Hernández','López','Martínez','Ramírez','Núñez','Ibáñez'])[1 + (n / 8) % 8]
					         || ' ' || n,
					       DATE '1950-01-01' + (n * 37) % 25000, CASE WHEN n % 2 = 0 THEN 'FEMENINO' ELSE 'MASCULINO' END,
					       '55' || lpad(n::text, 8, '0'), now()
					FROM generate_series(1, 500) AS n
					""").param(consultorio).update()));

			long inicio = System.nanoTime();
			buscar("jose nunez").andExpect(status().isOk());
			buscar("lucia").andExpect(status().isOk());
			Duration tiempo = Duration.ofNanos(System.nanoTime() - inicio);

			assertThat(tiempo).isLessThan(Duration.ofSeconds(1));
			buscar("jose nunez").andExpect(jsonPath("$.page.totalElements").value(
					org.hamcrest.Matchers.greaterThan(0)));
		}

	}

	@Nested
	class Edicion {

		@Test
		void editarConLaVersionActualIncrementaLaVersion() throws Exception {
			String id = idDe(registrar("Ana", "Ruiz", null));

			mvc.perform(titular(put(RUTA + "/" + id)).contentType(MediaType.APPLICATION_JSON)
					.content(cuerpo("Ana María", "Ruiz", null, 0L)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.nombre").value("Ana María"))
					.andExpect(jsonPath("$.version").value(1));
		}

		@Test
		void laSegundaDeDosEdicionesConLaMismaVersionRecibe409() throws Exception {
			String id = idDe(registrar("Ana", "Ruiz", null));

			// Dos personas abrieron al paciente en versión 0.
			mvc.perform(titular(put(RUTA + "/" + id)).contentType(MediaType.APPLICATION_JSON)
					.content(cuerpo("Ana (editó la titular)", "Ruiz", null, 0L)))
					.andExpect(status().isOk());
			mvc.perform(titular(put(RUTA + "/" + id)).contentType(MediaType.APPLICATION_JSON)
					.content(cuerpo("Ana (editó la colaboradora)", "Ruiz", null, 0L)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.versionActual").value(1));

			mvc.perform(titular(get(RUTA + "/" + id))).andExpect(jsonPath("$.nombre").value("Ana (editó la titular)"));
		}

		/**
		 * Dos ediciones que leen la misma versión y llegan casi al mismo tiempo: ambas pasan la
		 * comparación de versión, pero {@code @Version} hace fallar el commit de la segunda.
		 */
		@Test
		void dosTransaccionesSimultaneasNoSePisan() throws Exception {
			UUID id = UUID.fromString(idDe(registrar("Ana", "Ruiz", null)));
			var nueva = new TransactionTemplate(tx.getTransactionManager());
			nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
			var hoy = LocalDate.now(ZoneOffset.UTC);

			assertThatThrownBy(() -> TenantContext.ejecutarComo(consultorio, () -> tx.executeWithoutResult(primera -> {
				Paciente leido = repository.findById(id).orElseThrow();
				// Mientras la primera sigue abierta, otra edición termina antes.
				nueva.executeWithoutResult(segunda -> repository.findById(id).orElseThrow()
						.actualizar(DatosPersonales.de("Ana (gana)", "Ruiz", LocalDate.of(1988, 4, 17), Sexo.FEMENINO,
								null, null, null, hoy)));
				leido.actualizar(DatosPersonales.de("Ana (pierde)", "Ruiz", LocalDate.of(1988, 4, 17), Sexo.FEMENINO,
						null, null, null, hoy));
			}))).isInstanceOf(ObjectOptimisticLockingFailureException.class);

			mvc.perform(titular(get(RUTA + "/" + id))).andExpect(jsonPath("$.nombre").value("Ana (gana)"));
		}

		@Test
		void editarSinVersionDevuelve400() throws Exception {
			String id = idDe(registrar("Ana", "Ruiz", null));

			mvc.perform(titular(put(RUTA + "/" + id)).contentType(MediaType.APPLICATION_JSON)
					.content(cuerpo("Ana", "Ruiz", null, null)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores[0].campo").value("version"));
		}

	}

	@Nested
	class Baja {

		@Test
		void unPacienteDadoDeBajaNoApareceNiEnBusquedasPeroSeConserva() throws Exception {
			String id = idDe(registrar("Ana", "Baja", null));

			mvc.perform(titular(delete(RUTA + "/" + id))).andExpect(status().isNoContent());

			mvc.perform(titular(get(RUTA + "/" + id))).andExpect(status().isNotFound());
			buscar("ana baja").andExpect(jsonPath("$.content", hasSize(0)));
			buscar(null).andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));
			mvc.perform(titular(delete(RUTA + "/" + id))).andExpect(status().isNotFound());

			// La fila sigue en la base, con la fecha de baja.
			Integer conservados = TenantContext.ejecutarComo(consultorio, () -> tx.execute(estado -> jdbc.sql(
					"SELECT count(*) FROM paciente WHERE id = ? AND eliminado_en IS NOT NULL").param(UUID.fromString(id))
					.query(Integer.class).single()));
			assertThat(conservados).isEqualTo(1);
		}

		@Test
		void unaAsistenteNoPuedeDarDeBaja() throws Exception {
			String id = idDe(registrar("Ana", "Ruiz", null));

			mvc.perform(como(delete(RUTA + "/" + id), "ASISTENTE")).andExpect(status().isForbidden());
			mvc.perform(como(delete(RUTA + "/" + id), "COLABORADOR")).andExpect(status().isNoContent());
		}

		@Test
		void laAppNoPuedeBorrarPacientesDeVerdad() throws Exception {
			UUID id = UUID.fromString(idDe(registrar("Ana", "Ruiz", null)));

			assertThatThrownBy(() -> TenantContext.ejecutarComo(consultorio, () -> tx.executeWithoutResult(
					estado -> jdbc.sql("DELETE FROM paciente WHERE id = ?").param(id).update())))
					.rootCause().hasMessageContaining("permission denied for table paciente");
		}

	}

	@Nested
	class Aislamiento {

		@Test
		void unConsultorioNoVeLosPacientesDeOtro() throws Exception {
			String deEste = idDe(registrar("Ana", "Ruiz", null));
			UUID otro = UUID.randomUUID();
			String otroSlug = "pacientes-" + otro.toString().substring(0, 8);
			jdbc.sql("INSERT INTO tenant (id, slug, nombre) VALUES (?, ?, 'Otro')").params(otro, otroSlug).update();
			var delOtro = jwt().jwt(t -> t.claim("tenant_id", otro.toString()).claim("rol", "TITULAR"))
					.authorities(new SimpleGrantedAuthority("ROLE_TITULAR"));

			mvc.perform(get(RUTA).header("X-Tenant", otroSlug).with(delOtro))
					.andExpect(jsonPath("$.content", hasSize(0)));
			mvc.perform(get(RUTA + "/" + deEste).header("X-Tenant", otroSlug).with(delOtro))
					.andExpect(status().isNotFound());
			mvc.perform(put(RUTA + "/" + deEste).header("X-Tenant", otroSlug).with(delOtro)
					.contentType(MediaType.APPLICATION_JSON).content(cuerpo("Intruso", "Ruiz", null, 0L)))
					.andExpect(status().isNotFound());
		}

	}

	@Test
	void altaEdicionYBajaQuedanEnLaBitacora() throws Exception {
		String id = idDe(registrar("Ana", "Ruiz", null));
		mvc.perform(titular(put(RUTA + "/" + id)).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo("Ana María", "Ruiz", null, 0L))).andExpect(status().isOk());
		mvc.perform(titular(delete(RUTA + "/" + id))).andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(accionesAuditadas(UUID.fromString(id)))
				.containsExactly("PACIENTE_CREADO", "PACIENTE_ACTUALIZADO", "PACIENTE_DADO_DE_BAJA"));
	}

	private List<String> accionesAuditadas(UUID pacienteId) {
		return TenantContext.ejecutarComo(consultorio, () -> tx.execute(estado -> jdbc
				.sql("SELECT accion FROM auditoria WHERE recurso_id = ? ORDER BY id")
				.param(pacienteId).query(String.class).list()));
	}

}
