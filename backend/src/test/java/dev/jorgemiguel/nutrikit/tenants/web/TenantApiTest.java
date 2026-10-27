package dev.jorgemiguel.nutrikit.tenants.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;

/**
 * Prueba de punta a punta: HTTP, seguridad, servicio, JPA y Postgres real. Cada prueba corre en
 * una transacción que se revierte al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TenantApiTest {

	private static final String RUTA = "/api/operador/tenants";

	@Autowired
	private MockMvc mvc;

	private String crear(String slug) throws Exception {
		return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
				{"slug": "%s", "nombre": "Consultorio %s"}
				""".formatted(slug, slug)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
	}

	@Test
	void laUbicacionDelAltaApuntaAlRecursoCreado() throws Exception {
		String ubicacion = crear("nutri-ana");

		mvc.perform(get(ubicacion))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("nutri-ana"))
				.andExpect(jsonPath("$.estado").value("ACTIVO"));
	}

	@Test
	void elSegundoAltaConElMismoSlugDevuelve409() throws Exception {
		crear("nutri-ana");

		mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
				{"slug": "nutri-ana", "nombre": "Otro consultorio"}
				"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void unCanceladoNoSePuedeReactivar() throws Exception {
		String ubicacion = crear("nutri-ana");

		mvc.perform(patch(ubicacion + "/estado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"estado\": \"CANCELADO\"}"))
				.andExpect(status().isOk());

		mvc.perform(patch(ubicacion + "/estado").contentType(MediaType.APPLICATION_JSON)
				.content("{\"estado\": \"ACTIVO\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void listaPaginadoDelMasRecienteAlMasAntiguo() throws Exception {
		crear("primero");
		crear("segundo");
		crear("tercero");

		mvc.perform(get(RUTA).param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].slug").value("tercero"))
				.andExpect(jsonPath("$.page.totalElements").value(3))
				.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void ordenarPorUnCampoInexistenteDevuelve400() throws Exception {
		mvc.perform(get(RUTA).param("sort", "contrasena"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void elContratoOpenApiDocumentaLosEndpoints() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"" + RUTA + "\"")))
				.andExpect(content().string(containsString("\"" + RUTA + "/{id}/estado\"")))
				.andExpect(content().string(containsString("nutri-ana")));
	}

	@Test
	void swaggerUiEstaDisponible() throws Exception {
		mvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

}
