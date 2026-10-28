package dev.jorgemiguel.nutrikit.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import dev.jorgemiguel.nutrikit.TestcontainersConfiguration;

/** Solo el origen configurado (el servidor de Vite en desarrollo) puede llamar a la API desde un navegador. */
@SpringBootTest(properties = {
		"nutrikit.tenancy.cabecera-habilitada=true",
		"nutrikit.cors.origenes-permitidos=http://localhost:5173" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CorsTest {

	private static final String PERMITIDO = "http://localhost:5173";

	@Autowired
	private MockMvc mvc;

	private ResultActions preflight(String ruta, String origen, String cabeceras) throws Exception {
		return mvc.perform(options(ruta)
				.header(HttpHeaders.ORIGIN, origen)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, cabeceras));
	}

	@Test
	void elPreflightDelOrigenPermitidoPasaSinConsultorioNiToken() throws Exception {
		preflight("/api/pacientes", PERMITIDO, "authorization,content-type,x-tenant")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PERMITIDO))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	void elPreflightDeLaPaginaDelPacienteTambienPasa() throws Exception {
		preflight("/api/acceso/un-token", PERMITIDO, "x-pin,x-tenant")
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PERMITIDO));
	}

	@Test
	void otroOrigenSeRechaza() throws Exception {
		preflight("/api/auth/login", "https://sitio-malicioso.example", "content-type")
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void unaCabeceraNoPermitidaSeRechaza() throws Exception {
		preflight("/api/auth/login", PERMITIDO, "x-otra-cosa").andExpect(status().isForbidden());
	}

	@Test
	void laRespuestaRealLlevaLasCabecerasCors() throws Exception {
		mvc.perform(get("/api/consultorio").header(HttpHeaders.ORIGIN, PERMITIDO).header("X-Tenant", "no-existe"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PERMITIDO))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Retry-After"));
	}

}
