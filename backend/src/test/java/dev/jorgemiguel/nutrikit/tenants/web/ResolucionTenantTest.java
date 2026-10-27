package dev.jorgemiguel.nutrikit.tenants.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;
import dev.jorgemiguel.nutrikit.shared.TenantContext;
import dev.jorgemiguel.nutrikit.tenants.domain.Slug;
import dev.jorgemiguel.nutrikit.tenants.domain.Tenant;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModulo;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantModuloRepository;
import dev.jorgemiguel.nutrikit.tenants.domain.TenantRepository;

/** Identificación del consultorio en peticiones HTTP reales, de punta a punta. */
@SpringBootTest(properties = "nutrikit.tenancy.cabecera-habilitada=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ResolucionTenantTest {

	@Autowired
	private MockMvc mvc;

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
	private UUID suspendido;

	@BeforeEach
	void consultorios() {
		ana = tenants.save(Tenant.crear(Slug.of("resolucion-ana"), "Consultorio Ana")).getId();
		beto = tenants.save(Tenant.crear(Slug.of("resolucion-beto"), "Consultorio Beto")).getId();
		Tenant pausado = Tenant.crear(Slug.of("resolucion-pausado"), "Consultorio pausado");
		pausado.suspender();
		suspendido = tenants.save(pausado).getId();

		TenantContext.ejecutarComo(ana, () -> tx.executeWithoutResult(e -> modulos.save(TenantModulo.registrar("agenda", true))));
		TenantContext.ejecutarComo(beto, () -> tx.executeWithoutResult(e -> {
			modulos.save(TenantModulo.registrar("agenda", false));
			modulos.save(TenantModulo.registrar("planes", true));
		}));
	}

	/** JWT de un usuario del consultorio indicado; la seguridad exige que coincida con el de la petición. */
	private static RequestPostProcessor usuarioDe(UUID tenantId) {
		return jwt().jwt(token -> token.claim("tenant_id", tenantId.toString()).claim("rol", "TITULAR"))
				.authorities(new SimpleGrantedAuthority("ROLE_TITULAR"));
	}

	@AfterEach
	void borrarConsultorios() {
		jdbc.sql("DELETE FROM tenant WHERE id IN (?, ?, ?)").params(ana, beto, suspendido).update();
	}

	@Test
	void sinConsultorioLaPeticionSeRechazaCon400() throws Exception {
		mvc.perform(get("/api/consultorio"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Consultorio no identificado"))
				.andExpect(jsonPath("$.instance").value("/api/consultorio"));
	}

	@Test
	void identificaElConsultorioPorCabecera() throws Exception {
		mvc.perform(get("/api/consultorio").header("X-Tenant", "resolucion-ana").with(usuarioDe(ana)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ana.toString()))
				.andExpect(jsonPath("$.slug").value("resolucion-ana"));
	}

	@Test
	void identificaElConsultorioPorSubdominio() throws Exception {
		mvc.perform(get("/api/consultorio").with(usuarioDe(beto)).with(peticion -> {
			peticion.setServerName("resolucion-beto.nutrikit.mx");
			return peticion;
		}))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("resolucion-beto"));
	}

	@Test
	void unConsultorioInexistenteOSuspendidoDevuelve404() throws Exception {
		mvc.perform(get("/api/consultorio").header("X-Tenant", "no-existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Consultorio no disponible"));

		mvc.perform(get("/api/consultorio").header("X-Tenant", "resolucion-pausado"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Consultorio no disponible"));
	}

	@Test
	void cadaPeticionVeSoloLosModulosDeSuConsultorio() throws Exception {
		mvc.perform(get("/api/consultorio/modulos").header("X-Tenant", "resolucion-ana").with(usuarioDe(ana)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].modulo").value("agenda"))
				.andExpect(jsonPath("$[0].habilitado").value(true));

		mvc.perform(get("/api/consultorio/modulos").header("X-Tenant", "resolucion-beto").with(usuarioDe(beto)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].modulo").value("agenda"))
				.andExpect(jsonPath("$[0].habilitado").value(false));
	}

	@Test
	void elContextoSeLimpiaAlTerminarCadaPeticion() throws Exception {
		mvc.perform(get("/api/consultorio").header("X-Tenant", "resolucion-ana").with(usuarioDe(ana)))
				.andExpect(jsonPath("$.slug").value("resolucion-ana"));

		// MockMvc corre la petición en este mismo hilo: si el contexto no se limpiara, seguiría aquí.
		assertThat(TenantContext.actual()).isEmpty();

		// Y la siguiente petición en el mismo hilo, sin consultorio, no hereda el anterior.
		mvc.perform(get("/api/consultorio"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void elContextoSeLimpiaAunqueLaPeticionFalle() throws Exception {
		mvc.perform(get("/api/consultorio/no-existe").header("X-Tenant", "resolucion-ana").with(usuarioDe(ana)))
				.andExpect(status().isNotFound());

		assertThat(TenantContext.actual()).isEmpty();
	}

	@Test
	void lasRutasDeLaPlataformaNoRequierenConsultorio() throws Exception {
		mvc.perform(get("/api/operador/tenants").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
				.andExpect(status().isOk());
	}

}
